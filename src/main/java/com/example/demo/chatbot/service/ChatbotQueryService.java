package com.example.demo.chatbot.service;
import com.example.demo.config.CurrentLanguage;
import com.example.demo.config.LocalizedMessages;

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
    /** 문서 하나당 프롬프트에 넣는 최대 글자 수. 프롬프트가 커질수록 Gemini 응답이 느려져서 짧게 자른다.
     *  번역 모드에서는 "이야기 전체 보기"에 이 길이만큼만 번역돼 나오니, 원문이 짧은 설화 특성상
     *  대부분 다 담기게 넉넉히 잡았다(900 → 1600). */
    private static final int MAX_SOURCE_CHARS = 1600;
    private static final java.util.regex.Pattern HANGUL = java.util.regex.Pattern.compile("[\\uAC00-\\uD7A3]");

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
    private final LocalizedMessages messages;

    public ChatbotQueryService(GeminiEmbeddingClient embeddingClient,
                               GeminiChatClient chatClient,
                               VectorDocumentRepository vectorDocumentRepository,
                               List<SourceTextResolver> resolvers,
                               LocalizedMessages messages) {
        this.messages = messages;
        this.embeddingClient = embeddingClient;
        this.chatClient = chatClient;
        this.vectorDocumentRepository = vectorDocumentRepository;
        this.resolversByType = resolvers.stream()
                .collect(java.util.stream.Collectors.toMap(SourceTextResolver::sourceType, r -> r));
    }

    public ChatAskResponse ask(String question) {
        return ask(question, null, null);
    }

    /** imageBase64/imageMimeType이 있으면(사용자가 + 버튼으로 사진을 첨부한 경우) 질문 텍스트가
     *  없어도 되고, 벡터 검색으로 관련 부산 이야기를 못 찾아도 사진만 보고 답할 수 있다. */
    public ChatAskResponse ask(String question, String imageBase64, String imageMimeType) {
        String q = question == null ? "" : question.trim();
        boolean hasImage = imageBase64 != null && !imageBase64.isBlank();

        List<SourceTextResolver.ResolvedSource> resolved = new ArrayList<>();
        List<String> sourceTypes = new ArrayList<>();
        if (!q.isEmpty()) {
            float[] questionVector = embeddingClient.embed(q);
            List<VectorMatch> matches = vectorDocumentRepository.findNearest(null, questionVector, topK);
            for (VectorMatch match : matches) {
                if (match.distance() > maxDistance) continue;
                resolveSource(match).ifPresent(source -> {
                    resolved.add(source);
                    sourceTypes.add(match.sourceType());
                });
            }
        }

        if (resolved.isEmpty() && !hasImage) {
            // 화면 언어로. 예전에는 늘 국문(NO_MATCH_ANSWER)이었다.
            return new ChatAskResponse(messages.get("chatbot.answer.noMatch"), List.of());
        }

        // 참고 자료(제목+본문 전체)는 원문(한국어)이라, 답변만 질문 언어로 나오면 "참고한 이야기"
        // 카드/카드를 눌렀을 때 나오는 전문까지 계속 한국어로 보인다. 질문이 한국어가 아닐 때만
        // 같은 호출 안에서 자료별 번역본도 같이 받아온다(호출 한 번 더 안 씀). 한국어 질문일 땐
        // 어차피 원문 그대로 쓰면 되니 이 과정 자체를 건너뛰어 응답을 가볍게 유지한다.
        boolean needsTranslation = !q.isEmpty() && !looksKorean(q);
        String raw = chatClient.generate(buildPrompt(q, resolved, needsTranslation, hasImage), imageBase64, imageMimeType);
        // 모델이 지시를 안 지키고 구분자에 마크다운(** 등)을 붙이는 경우까지 감안해서 제거하고 본다.
        String normalized = raw.replace("**", "");

        String answer;
        List<String[]> translated;
        if (needsTranslation) {
            int cut = normalized.indexOf("===SOURCE 1 TITLE===");
            answer = (cut >= 0 ? normalized.substring(0, cut) : normalized).strip();
            translated = cut >= 0 ? parseTranslatedSources(normalized.substring(cut), resolved.size()) : List.of();
        } else {
            answer = normalized.strip();
            translated = List.of();
        }

        List<ChatAskResponse.Source> citedSources = new ArrayList<>();
        for (int i = 0; i < resolved.size(); i++) {
            SourceTextResolver.ResolvedSource source = resolved.get(i);
            String[] tr = i < translated.size() ? translated.get(i) : null;
            String title = (tr != null && !tr[0].isBlank()) ? tr[0] : source.title();
            String content = (tr != null && !tr[1].isBlank()) ? tr[1] : source.text();
            String summary = (tr != null && !tr[2].isBlank()) ? tr[2] : "";
            citedSources.add(new ChatAskResponse.Source(title, sourceTypes.get(i), source.imgUrl(), content, summary));
        }
        return new ChatAskResponse(answer, citedSources);
    }

    /** 질문에 한글이 하나라도 섞여 있으면 한국어 질문으로 본다 — 이때는 원문 그대로 써도 되니 번역을 안 시킨다. */
    private boolean looksKorean(String question) {
        return question != null && HANGUL.matcher(question).find();
    }

    /**
     * "===SOURCE N TITLE===" / "===SOURCE N SUMMARY===" / "===SOURCE N CONTENT===" 마커 사이의
     * 내용을 순서대로 꺼낸다(각 원소는 [title, content, summary]). 마커를 못 찾으면 그 이후
     * 자료는 건너뛰고 이미 찾은 것까지만 반환한다(나머지는 원문으로 대체됨).
     */
    private List<String[]> parseTranslatedSources(String block, int count) {
        List<String[]> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String titleMarker = "===SOURCE " + i + " TITLE===";
            String summaryMarker = "===SOURCE " + i + " SUMMARY===";
            String contentMarker = "===SOURCE " + i + " CONTENT===";
            int titleStart = block.indexOf(titleMarker);
            int summaryStart = block.indexOf(summaryMarker);
            int contentStart = block.indexOf(contentMarker);
            if (titleStart < 0 || summaryStart < 0 || contentStart < 0
                    || summaryStart < titleStart || contentStart < summaryStart) break;
            String title = block.substring(titleStart + titleMarker.length(), summaryStart).strip();
            String summary = block.substring(summaryStart + summaryMarker.length(), contentStart).strip();
            int nextTitleStart = block.indexOf("===SOURCE " + (i + 1) + " TITLE===", contentStart);
            String content = (nextTitleStart >= 0
                    ? block.substring(contentStart + contentMarker.length(), nextTitleStart)
                    : block.substring(contentStart + contentMarker.length())).strip();
            result.add(new String[]{title, content, summary});
        }
        return result;
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

    /**
     * 사진만 보냈을 때 답할 언어. 질문 글이 없어 언어를 알 수 없으니 화면 언어를 따른다.
     * 예전에는 늘 한국어로 답하게 해서, 일본어 화면에서도 한국어 답이 왔다.
     */
    private static String screenLanguageName() {
        return switch (CurrentLanguage.get()) {
            case "en" -> "영어(English)";
            case "ja" -> "일본어(日本語)";
            case "zh" -> "중국어 간체(简体中文)";
            case "fr" -> "프랑스어(Français)";
            default -> "한국어";
        };
    }

    private String buildPrompt(String question, List<SourceTextResolver.ResolvedSource> sources,
                                boolean needsTranslation, boolean hasImage) {
        StringBuilder sb = new StringBuilder();

        if (sources.isEmpty()) {
            // 벡터 검색으로 관련 부산 이야기를 못 찾았지만(또는 질문 텍스트 자체가 없지만) 사진이
            // 있는 경우 — ask()에서 이 분기는 hasImage가 true일 때만 호출된다.
            sb.append("당신은 부산 지역 여행·설화를 안내하는 챗봇입니다. 사용자가 사진을 보냈습니다. ")
              .append("사진 내용을 보고 자연스럽게 답하세요 — 부산의 장소·음식·설화와 관련 있어 보이면 그 내용을 곁들이고, ")
              .append("아니면 사진에 대해 편하게 설명해도 됩니다. 지어내지 말고, 확실하지 않으면 확실하지 않다고 답하세요. ")
              .append(question.isEmpty()
                      ? "질문 텍스트는 따로 없으니 " + screenLanguageName() + "로 답하세요. "
                      : "답변 언어는 반드시 사용자 질문에 쓰인 언어와 같게 하세요. ")
              .append("이 답변은 채팅창에 렌더링되니, 필요할 때만 **굵게**나 \"- 항목\" 목록을 섞어 쓰고 ")
              .append("#, ---, 코드블록 같은 다른 마크다운 문법은 쓰지 말고 3~5문장으로 간결하게 답하세요.\n\n");
            if (!question.isEmpty()) sb.append("질문: ").append(question);
            return sb.toString();
        }

        // sources는 벡터 거리순(가장 가까운 것이 0번)으로 정렬되어 들어온다.
        // 질문과 제일 가까운 자료 하나를 "주 자료"로 못박아, 매번 비슷한 톤으로 여러 자료를
        // 뭉뚱그려 답하는 대신 질문에 맞는 이야기 하나에 집중해서 구체적으로 답하게 한다.
        sb.append("당신은 부산 지역 설화·전설을 안내하는 챗봇입니다. 아래 [주 자료]를 중심으로 사용자 질문에 답변하세요. ")
          .append("답변 언어는 반드시 사용자 질문에 쓰인 언어와 같게 하세요 — 질문이 일본어면 일본어로, 영어면 영어로, ")
          .append("중국어면 중국어로, 프랑스어면 프랑스어로 답하고, 그 외 언어도 마찬가지로 질문 언어를 그대로 따르세요. ")
          .append("자료([주 자료]/[참고 자료])가 한국어로 되어 있어도 번역해서 질문 언어로 답변하세요. ")
          .append("[참고 자료]는 사용자가 비교나 다른 이야기를 원할 때만 보조로 쓰고, 질문과 상관없으면 언급하지 마세요. ")
          .append("자료에 없는 내용은 지어내지 말고, 모르면 모른다고 답하세요. ")
          .append("일반적이고 뻔한 설명 대신 [주 자료]에 나온 등장인물 이름, 지명, 사건 등 구체적인 디테일을 반드시 답변에 담아, ")
          .append("비슷한 질문에도 그 이야기만의 내용으로 답하세요. ")
          .append("질문에서 직접 묻지 않은 다른 이야기까지 곁들이지 말고, 질문 범위 안에서만 3~5문장으로 간결하게 답하세요. ");
        if (hasImage) {
            sb.append("사용자가 사진도 함께 보냈으니, 사진 내용을 참고해서 답변에 자연스럽게 반영하세요. ");
        }
        sb.append("이 답변은 채팅창에 렌더링되니 읽기 편하게 아래 서식만 필요할 때 적절히 섞어 쓰세요: ")
          .append("강조할 단어/이름은 **굵게**, 여러 항목을 나열할 땐 새 줄마다 \"- 항목\" 형식의 목록, ")
          .append("정보를 비교하거나 표로 정리하는 게 더 읽기 편할 땐 \"| 항목 | 내용 |\" 형식의 표를 쓰세요. ")
          .append("다만 답변 전체를 목록이나 표로만 채우지 말고 자연스러운 설명 문장을 기본으로 하고, ")
          .append("정말 정리해서 보여주는 게 나은 부분에만 목록/표를 곁들이세요. ")
          .append("#, ---, 코드블록 같은 다른 마크다운 문법은 쓰지 마세요.\n\n");
        for (int i = 0; i < sources.size(); i++) {
            SourceTextResolver.ResolvedSource source = sources.get(i);
            String text = source.text();
            if (text != null && text.length() > MAX_SOURCE_CHARS) text = text.substring(0, MAX_SOURCE_CHARS);
            String label = i == 0 ? "[주 자료]" : "[참고 자료 " + i + "]";
            sb.append(label).append(' ').append(source.title()).append('\n')
              .append(text).append("\n\n");
        }
        sb.append("질문: ").append(question.isEmpty() ? "(텍스트 질문 없음 — 첨부한 사진을 보고 답변)" : question);
        if (needsTranslation) {
            // 사용자가 "참고한 이야기" 카드를 누르면 제목과 본문 전체를 그대로 보여주는데,
            // 원문이 한국어라 질문 언어랑 안 맞았다. 답변 뒤에 자료별로 제목/요약/본문 번역본을
            // 정해진 마커 형식으로 이어 붙여서, 추가 API 호출 없이 카드에도 번역본을 쓸 수 있게 한다.
            // 본문 전체를 한 번에 펼치면 외국어 사용자 입장에서 가독성이 떨어진다는 피드백으로,
            // 짧은 요약(summary)도 같이 받아서 프론트가 요약을 먼저 보여주고 원할 때만 전문을 펼친다.
            sb.append("\n\n답변을 다 쓴 뒤, 위에 나열한 자료 ").append(sources.size())
              .append("개 각각에 대해 아래 형식을 정확히 반복해서 이어서 적으세요 ")
              .append("(마커 문구·순서를 그대로 지키고, 다른 설명은 절대 덧붙이지 마세요):\n");
            for (int i = 1; i <= sources.size(); i++) {
                sb.append("===SOURCE ").append(i).append(" TITLE===\n(").append(i).append("번 자료 제목을 질문 언어로 번역)\n");
                sb.append("===SOURCE ").append(i).append(" SUMMARY===\n(").append(i)
                  .append("번 자료 내용을 질문 언어로 2~3문장, 핵심만 간결하게 요약)\n");
                sb.append("===SOURCE ").append(i).append(" CONTENT===\n(").append(i)
                  .append("번 자료 본문 전체를 질문 언어로 자연스럽게, 요약하지 말고 빠짐없이 번역. ")
                  .append("원문의 문단 구분은 그대로 살려서 문단 사이에 빈 줄을 넣어 읽기 편하게 하세요)\n");
            }
        }
        return sb.toString();
    }
}
