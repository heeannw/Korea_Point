package com.example.demo.community.config;

import com.example.demo.community.service.PostImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 게시글 본문 이미지를 브라우저가 다시 받아갈 수 있게 열어 준다.
 *
 * <p>여는 범위를 {@code uploads/community} 한 곳으로 좁힌 게 핵심이다.
 * {@code uploads} 전체를 열면 바로 옆의 {@code uploads/owner-requests} 안에 있는
 * 사업자등록증·영업신고증까지 주소만 알면 누구나 받아갈 수 있게 된다.
 */
@Configuration
@RequiredArgsConstructor
public class CommunityUploadConfig implements WebMvcConfigurer {

    private final PostImageStorage storage;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(PostImageStorage.PUBLIC_PREFIX + "**")
                // Path.toUri() 는 없는 디렉터리엔 끝 슬래시를 안 붙인다.
                // 슬래시가 없으면 스프링이 상위 경로로 잘못 해석하므로 직접 붙인다.
                .addResourceLocations(location())
                .setCachePeriod(60 * 60 * 24 * 30);
    }

    private String location() {
        String uri = storage.root().toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
