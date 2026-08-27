package com.example.demo.chatbot.service;

import com.example.demo.chatbot.client.BusanStoryClient;
import com.example.demo.chatbot.client.GeminiEmbeddingClient;
import com.example.demo.chatbot.dto.BusanStoryDto;
import com.example.demo.chatbot.repository.VectorDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 부산 이야기 전체를 임베딩해서 vector_documents를 채운다.
 * 원문(contents)은 임베딩을 만드는 순간에만 메모리에 있다가, 벡터로 바뀌면 버려진다 — DB엔 저장 안 함.
 */
@Service
public class ChatbotIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotIngestionService.class);
    public static final String SOURCE_TYPE_BUSAN_STORY = "busan_story";
    /** Gemini 임베딩 입력 길이 제한을 넘지 않도록 원문을 앞부분만 잘라서 쓴다. */
    private static final int MAX_EMBED_CHARS = 3000;

    private final BusanStoryClient busanStoryClient;
    private final GeminiEmbeddingClient embeddingClient;
    private final VectorDocumentRepository vectorDocumentRepository;

    public ChatbotIngestionService(BusanStoryClient busanStoryClient,
                                   GeminiEmbeddingClient embeddingClient,
                                   VectorDocumentRepository vectorDocumentRepository) {
        this.busanStoryClient = busanStoryClient;
        this.embeddingClient = embeddingClient;
        this.vectorDocumentRepository = vectorDocumentRepository;
    }

    public IngestResult ingestBusanStories() {
        List<BusanStoryDto> stories = busanStoryClient.findAll();
        int success = 0;
        int failed = 0;
        for (BusanStoryDto story : stories) {
            try {
                float[] vector = embeddingClient.embed(buildEmbeddingText(story));
                String sourceId = busanStoryClient.sourceKey(story);
                vectorDocumentRepository.upsert(SOURCE_TYPE_BUSAN_STORY, sourceId, story.getTitle(), vector);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("이야기 임베딩 실패, 건너뜁니다 (title={}): {}", story.getTitle(), e.getMessage());
            }
        }
        log.info("부산 이야기 임베딩 완료: 전체 {}건 / 성공 {}건 / 실패 {}건", stories.size(), success, failed);
        return new IngestResult(stories.size(), success, failed);
    }

    private String buildEmbeddingText(BusanStoryDto story) {
        String combined = String.join("\n",
                nullToEmpty(story.getTitle()),
                nullToEmpty(story.getSubtitle()),
                nullToEmpty(story.getContents()));
        return combined.length() > MAX_EMBED_CHARS ? combined.substring(0, MAX_EMBED_CHARS) : combined;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record IngestResult(int total, int success, int failed) {}
}
