package com.example.demo.chatbot.service;

import com.example.demo.chatbot.client.BusanStoryClient;
import com.example.demo.chatbot.client.GeminiEmbeddingClient;
import com.example.demo.chatbot.dto.BusanStoryDto;
import com.example.demo.chatbot.repository.VectorDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

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
    /**
     * 요청 사이에 간격을 둬서 분당 호출 한도(RPM)에 한 번에 몰리지 않게 한다.
     * 무료 등급 RPM 한도를 정확히 몰라서(17건만에 429가 났음) 넉넉히 4초로 잡는다.
     * 254건 기준 총 17분 정도 걸리지만, 한 번만 돌리면 되는 배치라 속도보다 안정성을 우선한다.
     */
    private static final long DELAY_BETWEEN_CALLS_MS = 4000;

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
        Set<String> alreadyDone = vectorDocumentRepository.findExistingSourceIds(SOURCE_TYPE_BUSAN_STORY);

        int success = 0;
        int failed = 0;
        int skipped = 0;
        for (BusanStoryDto story : stories) {
            String sourceId = busanStoryClient.sourceKey(story);
            if (alreadyDone.contains(sourceId)) {
                skipped++;
                continue;
            }
            try {
                float[] vector = embeddingClient.embed(buildEmbeddingText(story));
                vectorDocumentRepository.upsert(SOURCE_TYPE_BUSAN_STORY, sourceId, story.getTitle(), vector);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("이야기 임베딩 실패, 건너뜁니다 (title={}): {}", story.getTitle(), e.getMessage());
            }
            sleep(DELAY_BETWEEN_CALLS_MS);
        }
        log.info("부산 이야기 임베딩 완료: 전체 {}건 / 신규성공 {}건 / 이미완료(건너뜀) {}건 / 실패 {}건",
                stories.size(), success, skipped, failed);
        return new IngestResult(stories.size(), success, failed);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
