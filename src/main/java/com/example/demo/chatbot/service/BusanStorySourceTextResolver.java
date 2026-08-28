package com.example.demo.chatbot.service;

import com.example.demo.chatbot.client.BusanStoryClient;
import com.example.demo.chatbot.dto.BusanStoryDto;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 부산 이야기 source_id로 원문을 다시 가져온다. DB엔 없어서 매번 API를 다시 호출한다. */
@Component
public class BusanStorySourceTextResolver implements SourceTextResolver {

    private final BusanStoryClient busanStoryClient;

    public BusanStorySourceTextResolver(BusanStoryClient busanStoryClient) {
        this.busanStoryClient = busanStoryClient;
    }

    @Override
    public String sourceType() {
        return ChatbotIngestionService.SOURCE_TYPE_BUSAN_STORY;
    }

    @Override
    public Optional<ResolvedSource> resolve(String sourceId) {
        return busanStoryClient.findBySourceKey(sourceId).map(this::toResolvedSource);
    }

    private ResolvedSource toResolvedSource(BusanStoryDto story) {
        String text = String.join("\n",
                nullToEmpty(story.getSubtitle()),
                nullToEmpty(story.getContents()));
        return new ResolvedSource(story.getTitle(), text, normalizeImageUrl(story.getImgUrl()));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 채팅창은 https라 http 이미지가 섞이면 브라우저가 막을 수 있어 https로 맞춘다. */
    private String normalizeImageUrl(String url) {
        if (url == null || url.isBlank()) return null;
        return url.startsWith("http://") ? "https://" + url.substring(7) : url;
    }
}
