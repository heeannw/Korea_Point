package com.example.demo.chatbot.service;

import com.example.demo.chatbot.client.GeminiChatClient;
import com.example.demo.chatbot.client.GeminiEmbeddingClient;
import com.example.demo.chatbot.dto.ChatAskResponse;
import com.example.demo.chatbot.repository.VectorDocumentRepository;
import com.example.demo.chatbot.repository.VectorDocumentRepository.VectorMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 챗봇 질문-답변 파이프라인.
 * 질문 임베딩 → pgvector 유사도 검색(벡터만 비교) → 찾은 source_id로 원문 실시간 재조회
 * → 원문을 근거로 Gemini에게 답변 생성 요청. DB에서 원문을 직접 꺼내오는 단계는 없다.
 */
@Service
public class ChatbotQueryService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotQueryService.class);
    private static final String NO_MATCH_ANSWER =
            "관련된 부산 이야기를 찾지 못했어요. 다른 질문으로 다시 물어봐 주세요.";
    /** 문서 하나당 프롬프트에 넣는 최대 글자 수. 프롬프트가 커질수록 Gemini 응답이 느려져서 짧게 자른다. */
    private static final int MAX_SOURCE_CHARS = 900;

    @Value("${chatbot.query.top-k:3}")
    private int topK;

    /** pgvector 코사인 거리 임계값. 이보다 멀면(관련 없으면) 근거로 안 쓴다.
     *  너무 크면 질문과 약하게만 관련된 자료까지 섞여 들어가 답변이 뭉뚱그려지고 비슷비슷해진다. */
    @Value("${chatbot.query.max-distance:0.45}")
    private double maxDistance;

    private final GeminiEmbeddingClient embeddingClient;
    private final GeminiChatClient chatClient;
    private final VectorDocumentRepository vectorDocumentRepository;
    private final Map<String, SourceTextResolver> resolversByType;

    public ChatbotQueryService(GeminiEmbeddingClient embeddingClient,
                               GeminiChatClient chatClient,
                               VectorDocumentRepository vectorDocumentRepository,
                               List<SourceTextResolver> resolvers) {
        this.embeddingClient = embeddingClient;
        this.chatClient = chatClient;
        this.vectorDocumentRepository = vectorDocumentRepository;
        this.resolversByType = resolvers.stream()
                .collect(java.util.stream.Collectors.toMap(SourceTextResolver::sourceType, r -> r));
    }

    public ChatAskResponse ask(String question) {
        float[] questionVector = embeddingClient.embed(question);
        List<VectorMatch> matches = vectorDocumentRepository.findNearest(null, questionVector, topK);

        List<SourceTextResolver.ResolvedSource> resolved = new ArrayList<>();
        List<ChatAskResponse.Source> citedSources = new ArrayList<>();
        for (VectorMatch match : matches) {
            if (match.distance() > maxDistance) continue;
            resolveSource(match).ifPresent(source -> {
                resolved.add(source);
                citedSources.add(new ChatAskResponse.Source(
                        source.title(), match.sourceType(), source.imgUrl(), source.text()));
            });
        }

        if (resolved.isEmpty()) {
            return new ChatAskResponse(NO_MATCH_ANSWER, List.of());
        }

        String answer = chatClient.generate(buildPrompt(question, resolved));
        return new ChatAskResponse(answer, citedSources);
    }

    private Optional<SourceTextResolver.ResolvedSource> resolveSource(VectorMatch match) {
        SourceTextResolver resolver = resolversByType.get(match.sourceType());
        if (resolver == null) {
            log.warn("source_type '{}'을 처리할 리졸버가 없습니다.", match.sourceType());
            return Optional.empty();
        }
        try {
            return resolver.resolve(match.sourceId());
        } catch (Exception e) {
            log.warn("원문 재조회 실패 (sourceType={}, sourceId={}): {}", match.sourceType(), match.sourceId(), e.getMessage());
            return Optional.empty();
        }
    }

    private String buildPrompt(String question, List<SourceTextResolver.ResolvedSource> sources) {
        // sources는 벡터 거리순(가장 가까운 것이 0번)으로 정렬되어 들어온다.
        // 질문과 제일 가까운 자료 하나를 "주 자료"로 못박아, 매번 비슷한 톤으로 여러 자료를
        // 뭉뚱그려 답하는 대신 질문에 맞는 이야기 하나에 집중해서 구체적으로 답하게 한다.
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 부산 지역 설화·전설을 안내하는 챗봇입니다. 아래 [주 자료]를 중심으로 사용자 질문에 한국어로 답변하세요. ")
          .append("[참고 자료]는 사용자가 비교나 다른 이야기를 원할 때만 보조로 쓰고, 질문과 상관없으면 언급하지 마세요. ")
          .append("자료에 없는 내용은 지어내지 말고, 모르면 모른다고 답하세요. ")
          .append("일반적이고 뻔한 설명 대신 [주 자료]에 나온 등장인물 이름, 지명, 사건 등 구체적인 디테일을 반드시 답변에 담아, ")
          .append("비슷한 질문에도 그 이야기만의 내용으로 답하세요. ")
          .append("질문에서 직접 묻지 않은 다른 이야기까지 곁들이지 말고, 질문 범위 안에서만 3~5문장으로 간결하게 답하세요. ")
          .append("이 답변은 채팅창에 그대로 표시되니 마크다운 문법(#, *, -, --- 등)은 쓰지 말고 순수한 문장으로만 답하세요.\n\n");
        for (int i = 0; i < sources.size(); i++) {
            SourceTextResolver.ResolvedSource source = sources.get(i);
            String text = source.text();
            if (text != null && text.length() > MAX_SOURCE_CHARS) text = text.substring(0, MAX_SOURCE_CHARS);
            String label = i == 0 ? "[주 자료]" : "[참고 자료 " + i + "]";
            sb.append(label).append(' ').append(source.title()).append('\n')
              .append(text).append("\n\n");
        }
        sb.append("질문: ").append(question);
        return sb.toString();
    }
}
