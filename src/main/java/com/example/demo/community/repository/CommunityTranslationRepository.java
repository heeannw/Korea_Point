package com.example.demo.community.repository;

import com.example.demo.community.entity.CommunityTranslation;
import com.example.demo.community.entity.CommunityTranslationTarget;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommunityTranslationRepository extends JpaRepository<CommunityTranslation, Long> {

    /** 화면에 뜰 글·댓글의 번역을 한 번에 가져온다. */
    List<CommunityTranslation> findByTargetTypeAndLanguageAndTargetIdIn(
            CommunityTranslationTarget targetType, String language, Collection<Long> targetIds);

    /**
     * 아직 이 언어로 안 옮긴 글.
     *
     * <p>큐를 따로 두지 않고 표 자체를 할 일 목록으로 쓴다. 서버를 다시 띄워도 남고,
     * 새 글이 올라오면 다음 차례에 저절로 잡힌다.
     */
    @Query("""
            SELECT p.id FROM Post p
             WHERE p.deletedAt IS NULL
               AND NOT EXISTS (
                   SELECT 1 FROM CommunityTranslation t
                    WHERE t.targetType = com.example.demo.community.entity.CommunityTranslationTarget.POST
                      AND t.targetId = p.id
                      AND t.language = :language)
             ORDER BY p.id DESC
            """)
    List<Long> findPostIdsMissing(@Param("language") String language, Pageable pageable);

    @Query("""
            SELECT c.id FROM Comment c
             WHERE c.deletedAt IS NULL
               AND c.content IS NOT NULL
               AND NOT EXISTS (
                   SELECT 1 FROM CommunityTranslation t
                    WHERE t.targetType = com.example.demo.community.entity.CommunityTranslationTarget.COMMENT
                      AND t.targetId = c.id
                      AND t.language = :language)
             ORDER BY c.id DESC
            """)
    List<Long> findCommentIdsMissing(@Param("language") String language, Pageable pageable);

    /** 글·댓글이 고쳐지면 옛 번역을 지운다. 보충 작업이 다시 채운다. */
    void deleteByTargetTypeAndTargetId(CommunityTranslationTarget targetType, Long targetId);

    boolean existsByTargetTypeAndTargetIdAndLanguage(
            CommunityTranslationTarget targetType, Long targetId, String language);
}
