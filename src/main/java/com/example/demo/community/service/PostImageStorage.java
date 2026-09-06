package com.example.demo.community.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.util.UUID;

/**
 * 게시글 본문에 넣는 이미지를 파일로 저장한다.
 *
 * <p>이걸 만들기 전에는 Quill 이 이미지를 base64 로 본문 HTML 에 통째로 박았다.
 * 글 하나가 수 MB 가 되고, 목록은 미리보기를 만들려고 본문 전체를 읽으므로
 * 글이 쌓이면 목록 한 페이지에서 수십 MB 를 읽게 된다. 본문에는 주소만 남긴다.
 */
@Slf4j
@Service
public class PostImageStorage {

    /** 본문 이미지 한 장의 상한. 전체 업로드 상한(10MB)보다 낮게 잡는다. */
    private static final long MAX_BYTES = 5 * 1024 * 1024;

    /** 브라우저가 이 주소로 이미지를 다시 요청한다. */
    public static final String PUBLIC_PREFIX = "/uploads/community/";

    private final Path root;

    public PostImageStorage(@Value("${community.image.upload-dir:uploads/community}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    /**
     * 이미지를 저장하고 브라우저가 쓸 주소를 돌려준다.
     *
     * @throws IllegalArgumentException 이미지가 아니거나 너무 클 때
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("이미지가 비어 있습니다.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("이미지는 5MB 까지 올릴 수 있습니다.");
        }

        byte[] head = readHead(file);
        ImageKind kind = ImageKind.sniff(head);
        if (kind == null) {
            // 확장자나 Content-Type 은 보내는 쪽이 마음대로 정한다. 실제 내용으로 판단한다.
            // 이렇게 해야 svg·html 을 .png 로 이름만 바꿔 올려서 XSS 를 심는 걸 막을 수 있다.
            throw new IllegalArgumentException("JPG, PNG, GIF, WEBP 이미지만 올릴 수 있습니다.");
        }

        // 파일명은 우리가 만든다. 올린 쪽 이름을 쓰면 경로 조작과 확장자 위조가 열린다.
        String name = UUID.randomUUID() + kind.extension;
        Path directory = root.resolve(YearMonth.now().toString());
        Path destination = directory.resolve(name);

        try {
            Files.createDirectories(directory);
            file.transferTo(destination);
        } catch (IOException ex) {
            log.error("본문 이미지 저장 실패: {}", destination, ex);
            throw new IllegalStateException("이미지 저장에 실패했습니다.", ex);
        }

        return PUBLIC_PREFIX + YearMonth.now() + "/" + name;
    }

    private byte[] readHead(MultipartFile file) {
        try (var in = file.getInputStream()) {
            return in.readNBytes(12);
        } catch (IOException ex) {
            throw new IllegalStateException("이미지를 읽지 못했습니다.", ex);
        }
    }

    /** 파일 앞부분 시그니처로 종류를 가린다. */
    private enum ImageKind {
        JPEG(".jpg"), PNG(".png"), GIF(".gif"), WEBP(".webp");

        final String extension;

        ImageKind(String extension) {
            this.extension = extension;
        }

        static ImageKind sniff(byte[] h) {
            if (h.length < 12) {
                return null;
            }
            if (u(h[0]) == 0xFF && u(h[1]) == 0xD8 && u(h[2]) == 0xFF) {
                return JPEG;
            }
            if (u(h[0]) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                    && u(h[4]) == 0x0D && u(h[5]) == 0x0A && u(h[6]) == 0x1A && u(h[7]) == 0x0A) {
                return PNG;
            }
            if (h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8'
                    && (h[4] == '7' || h[4] == '9') && h[5] == 'a') {
                return GIF;
            }
            if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
                return WEBP;
            }
            return null;
        }

        private static int u(byte b) {
            return b & 0xFF;
        }
    }
}
