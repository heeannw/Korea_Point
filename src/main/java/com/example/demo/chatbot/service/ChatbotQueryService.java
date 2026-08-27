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
    /** 문서 하나당 프롬프트에 넣는 최대 글자 수. 근거가 여러 개라 임베딩 때보다 짧게 자른다. */
    private static final int MAX_SOURCE_CHARS = 1500;

    @Value("${chatbot.query.top-k:5}")
    private int topK;

    /** pgvector 코사인 거리 임계값. 이보다 멀면(관련 없으면) 근거로 안 쓴다. */
    @Value("${chatbot.query.max-distance:0.6}")
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
                citedSources.add(new ChatAskResponse.Source(source.title(), match.sourceType()));
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
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 부산 지역 이야기 자료입니다. 이 자료만 근거로 삼아 사용자 질문에 한국어로 친절하게 답변하세요. ")
          .append("자료에 없는 내용은 지어내지 말고, 모르면 모른다고 답하세요. ")
          .append("자료가 여러 개라도 전부 나열하지 말고 질문과 가장 관련 있는 것 위주로 3~5문장 정도로 간결하게 답하세요. ")
          .append("이 답변은 채팅창에 그대로 표시되니 마크다운 문법(#, *, -, --- 등)은 쓰지 말고 순수한 문장으로만 답하세요.\n\n");
        for (int i = 0; i < sources.size(); i++) {
            SourceTextResolver.ResolvedSource source = sources.get(i);
            String text = source.text();
            if (text != null && text.length() > MAX_SOURCE_CHARS) text = text.substring(0, MAX_SOURCE_CHARS);
            sb.append("[자료 ").append(i + 1).append("] ").append(source.title()).append('\n')
              .append(text).append("\n\n");
        }
        sb.append("질문: ").append(question);
        return sb.toString();
    }
}
