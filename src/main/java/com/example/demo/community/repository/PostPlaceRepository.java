package com.example.demo.community.repository;

import com.example.demo.community.entity.PostPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostPlaceRepository extends JpaRepository<PostPlace, Long> {

    List<PostPlace> findByPostIdOrderBySortOrder(Long postId);

    void deleteByPostId(Long postId);

    /** 이 장소를 태그한 글 id 목록 (상세 패널 역참조용). */
    @Query("SELECT pp.postId FROM PostPlace pp WHERE pp.contentId = :contentId")
    List<Long> findPostIdsByContentId(@Param("contentId") String contentId);
}
