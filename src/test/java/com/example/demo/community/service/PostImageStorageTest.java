package com.example.demo.community.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 본문 이미지 업로드 검증.
 *
 * <p>확장자와 Content-Type 은 올리는 쪽이 마음대로 정하므로 믿지 않는다.
 * 파일 내용으로만 판단하는지 확인한다.
 */
class PostImageStorageTest {

    @TempDir
    Path tempDir;

    private PostImageStorage storage() {
        return new PostImageStorage(tempDir.toString());
    }

    private static final byte[] PNG_HEAD = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };

    @Test
    void PNG는_저장되고_주소를_돌려준다() throws IOException {
        String url = storage().store(new MockMultipartFile(
                "file", "photo.png", "image/png", PNG_HEAD));

        assertThat(url).startsWith("/uploads/community/").endsWith(".png");

        Path saved = tempDir.resolve(url.substring("/uploads/community/".length()));
        assertThat(Files.exists(saved)).isTrue();
        assertThat(Files.readAllBytes(saved)).isEqualTo(PNG_HEAD);
    }

    @Test
    void 올린_쪽_파일명은_쓰지_않는다() {
        // 경로 조작과 확장자 위조를 막으려면 이름을 우리가 지어야 한다
        String url = storage().store(new MockMultipartFile(
                "file", "../../evil.png", "image/png", PNG_HEAD));

        assertThat(url).doesNotContain("evil").doesNotContain("..");
    }

    @Test
    void 이미지가_아니면_거부한다() {
        // .png 로 이름만 바꾼 HTML. 이게 통과하면 관리자·다른 사용자 브라우저에서 스크립트가 돈다
        byte[] html = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storage().store(new MockMultipartFile(
                "file", "photo.png", "image/png", html)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미지만");
    }

    @Test
    void SVG도_거부한다() {
        // SVG 는 스크립트를 품을 수 있어 이미지로 취급하지 않는다
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storage().store(new MockMultipartFile(
                "file", "photo.svg", "image/svg+xml", svg)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 너무_크면_거부한다() {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG_HEAD, 0, big, 0, PNG_HEAD.length);

        assertThatThrownBy(() -> storage().store(new MockMultipartFile(
                "file", "big.png", "image/png", big)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void 빈_파일은_거부한다() {
        assertThatThrownBy(() -> storage().store(new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
