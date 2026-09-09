package com.example.demo.community.service;

import com.example.demo.community.config.CommunityProperties;
import com.example.demo.community.dto.PostDto;
import com.example.demo.community.entity.Post;
import com.example.demo.community.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 커뮤니티 목록 오른쪽 레일.
 *
 * <p>가게 홍보 글만 담는다. 노출 중인 글이 없으면 화면에서 그 칸이 통째로 사라진다.
 *
 * <p>한때 부산 축제도 함께 띄웠지만 팀 회의에서 뺐다. 덕분에 커뮤니티는
 * TourAPI 를 전혀 쓰지 않는다 — 외부 API 가 멈춰도 이 화면은 영향을 받지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CommunitySidebarService {

    private final PostService postService;
    private final PostRepository postRepository;
    private final CommunityProperties communityProperties;

    /** 무작위로 뽑기 전에 가져올 후보 수. 이보다 많아지면 그때 다른 방식을 고민한다. */
    private static final int CANDIDATE_LIMIT = 100;

    /**
     * 레일에 띄울 가게 홍보 글.
     *
     * <p>노출 기간이 남은 홍보 글 중에서 <b>무작위로</b> 뽑는다.
     * 최신순으로 뽑으면 늦게 산 사장님이 자리를 다 먹고, 먼저 500P 를 낸 사람이
     * 새 글이 올라오는 순간 밀린다. 무작위면 노출 중인 모두가 같은 기대 노출을 얻는다.
     */
    @Transactional(readOnly = true)
    public List<PostDto> promoPosts() {
        List<Post> candidates = new ArrayList<>(postRepository.findActivePromoPosts(
                communityProperties.getPromoCategoryId(),
                LocalDateTime.now(),
                PageRequest.of(0, CANDIDATE_LIMIT)));

        Collections.shuffle(candidates);
        return candidates.stream()
                .limit(communityProperties.getPromoRailSize())
                .map(postService::toListDto)
                .toList();
    }
}
