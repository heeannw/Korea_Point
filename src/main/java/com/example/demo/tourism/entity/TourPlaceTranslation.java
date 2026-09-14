package com.example.demo.tourism.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 빌드 때 뽑아둔 관광 번역표에 없던 항목을, 화면에 뜬 뒤 채워 넣은 자리.
 *
 * <p>번역표({@code tourdata/tourism/tour_i18n_<lang>.json})는 어느 시점의 스냅숏이다.
 * 관광공사에 그 뒤로 올라온 축제·장소는 표에 없어서 영어·일본어 화면에도 이름과 주소가
 * 국문으로 나왔다. 축제는 날짜에 따라 목록이 계속 바뀌니 표를 다시 뽑아도 같은 일이 반복된다.
 * 그래서 빠진 것을 만나면 그때 번역해 여기에 쌓아 두고, 다음부터는 여기서 꺼내 쓴다.
 *
 * <p>표를 고치지 않고 표 옆에 두는 이유는 표가 jar 안에 들어가 서버에서 쓸 수 없기 때문이다.
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
