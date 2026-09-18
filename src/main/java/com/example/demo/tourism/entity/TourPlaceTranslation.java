package com.example.demo.tourism.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@link com.example.demo.tourism.config.TourPlaceI18n}이 언어별 공식 서비스에서 못 찾은
 * 항목을, 화면에 뜬 뒤 DeepL로 채워 넣은 자리.
 *
 * <p>언어별 공식 서비스(Eng/Jpn/Chs/Fre)는 국문과 contentId 체계가 달라 매칭되는 항목이 거의
 * 없고, 축제는 날짜에 따라 목록이 계속 바뀌어 어차피 매번 새로 번역해야 한다. 그래서 못 찾은
 * 것을 만나면 그때 번역해 여기에 쌓아 두고, 다음부터는 여기서 꺼내 쓴다. 서버를 다시 띄워도
 * 남는다.
 */
@Entity
@Table(name = "tour_place_translation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"content_id", "language"}))
@Getter
@Setter
@NoArgsConstructor
public class TourPlaceTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", nullable = false, length = 50)
    private String contentId;

    /** en / ja / zh / fr. 국문은 원문이라 담지 않는다. */
    @Column(nullable = false, length = 10)
    private String language;

    @Column(length = 500)
    private String title;

    @Column(length = 500)
    private String address;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public TourPlaceTranslation(String contentId, String language, String title, String address) {
        this.contentId = contentId;
        this.language = language;
        this.title = title;
        this.address = address;
        this.createdAt = LocalDateTime.now();
    }
}
