package com.example.demo.community;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.dto.LocalUserDetails;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 커뮤니티 화면이 실제로 그려지는지 확인한다.
 *
 * <p>브라우저로 열어 보려면 로그인이 필요해 손으로 확인하기 어렵다. 그런데 화면을
 * 안 열어 보면 타임리프 식이 틀려도 컴파일은 통과해서 배포 뒤에야 알게 된다.
 * 여기서 서버가 실제로 HTML 을 만들어 보게 해 그 구멍을 막는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommunityPageRenderTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    com.example.demo.community.repository.CategoryRepository categoryRepository;

    @Autowired
    com.example.demo.community.config.CommunityProperties communityProperties;

    @Autowired
    com.example.demo.community.repository.PostRepository postRepository;

    /**
     * 앱이 principal 에서 사용자 id 를 꺼내 쓰므로 @WithMockUser 만으로는 부족하다.
     * 실제로 쓰는 LocalUserDetails 를 넣어 준다.
     */
    private org.springframework.security.core.Authentication asRealUser() {
        User user = userRepository.findAll().stream()
                .filter(u -> "USER".equals(u.getRole()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("USER 계정이 없어 확인할 수 없습니다"));
        LocalUserDetails details = new LocalUserDetails(user);
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                details, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    /**
     * 화면 틀이 요청한 언어로 나오는지 본다.
     *
     * <p>messages_ja/zh/fr 에 community 키가 없던 동안, 일본어로 커뮤니티를 열면
     * 글은 한국어이고 틀은 영어로 나왔다. 키가 빠져도 화면은 멀쩡히 그려지고
     * 영어로 조용히 넘어가기 때문에 눈으로 보지 않으면 모른다. 그래서 여기서 잡는다.
     *
     * <p>글 제목과 본문은 아직 국문·영문 두 벌뿐이라 검사하지 않는다. 여기서 보는 것은
     * 버튼·탭·안내 문구처럼 messages 파일에서 오는 것뿐이다.
     */
    @Test
    void 목록_틀이_요청한_언어로_나온다() throws Exception {
        // 글 쓰기 단추(community.btn.write)와 탭 제목으로 본다. 둘 다 messages 파일에서 온다.
        String[][] cases = {
                {"en", "Write Post", "Korea Point | Community"},
                {"ja", "記事の作成", "Korea Point | コミュニティ"},
                {"zh", "撰写文章", "Korea Point | 社区"},
                {"fr", "Écrire un article", "Korea Point | Communauté"},
        };
        for (String[] c : cases) {
            mockMvc.perform(get("/community/post-list")
                            .param("lang", c[0])
                            .with(authentication(asRealUser())))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(c[1])))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(c[2])))
                    // html lang 이 en 으로 박혀 있던 적이 있다. 화면 읽어주는 프로그램이 이걸 본다.
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("<html lang=\"" + c[0] + "\"")));
        }
    }

    /** 신고 창은 문구가 템플릿에 박혀 있어 오래 한국어·영어 두 벌뿐이었다. */
    @Test
    void 신고_창_문구도_언어를_탄다() throws Exception {
        String[][] cases = {{"ja", "通報"}, {"zh", "举报"}, {"fr", "Signaler"}};
        Long postId = postRepository.findAll().stream()
                .filter(p -> p.getDeletedAt() == null)
                .map(p -> p.getId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("글이 없어 확인할 수 없습니다"));
        for (String[] c : cases) {
            mockMvc.perform(get("/community/post/" + postId)
                            .param("lang", c[0])
                            .with(authentication(asRealUser())))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(c[1])));
        }
    }

    @Test
    void 목록_화면이_그려진다() throws Exception {
        mockMvc.perform(get("/community/post-list").with(authentication(asRealUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("js-roll-track")));
    }

    @Test
    void 베스트_보기가_그려진다() throws Exception {
        mockMvc.perform(get("/community/post-list").param("view", "best")
                        .with(authentication(asRealUser())))
                .andExpect(status().isOk())
                // 베스트에서만 순위가 붙는다
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"rank shrink-0")));
    }

    /** 베스트를 봐도 커뮤니티를 떠나면 안 된다. 탭·검색·레일이 그대로 있어야 한다. */
    @Test
    void 베스트_보기도_주변_UI가_유지된다() throws Exception {
        mockMvc.perform(get("/community/post-list").param("view", "best")
                        .with(authentication(asRealUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("js-roll-track")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cat-badge")));
    }

    @Test
    void 전체글_보기에는_순위가_없다() throws Exception {
        mockMvc.perform(get("/community/post-list").with(authentication(asRealUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("class=\"rank shrink-0"))));
    }

    /**
     * 카테고리를 고른 채 베스트를 눌러도 카테고리가 유지돼야 한다.
     *
     * <p>탭과 베스트가 서로의 값을 안 넘겨서 조용히 전체로 튀던 것을 막는다.
     */
    @Test
    void 베스트에서_카테고리가_유지된다() throws Exception {
        Long categoryId = categoryRepository.findAll().stream()
                .map(c -> c.getId()).findFirst()
                .orElseThrow(() -> new IllegalStateException("카테고리가 없어 확인할 수 없습니다"));

        String html = mockMvc.perform(get("/community/post-list")
                        .param("view", "best").param("categoryId", String.valueOf(categoryId))
                        .with(authentication(asRealUser())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 기간 링크가 카테고리를 계속 달고 다녀야 다음 클릭에서도 안 풀린다
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("categoryId=" + categoryId);
    }

    /** 카테고리별 베스트는 처음에 전체 기간으로 연다 — 좁히면 자주 비기 때문이다. */
    @Test
    void 카테고리별_베스트는_전체_기간으로_열린다() throws Exception {
        Long categoryId = categoryRepository.findAll().stream()
                .map(c -> c.getId()).findFirst().orElseThrow();

        mockMvc.perform(get("/community/post-list")
                        .param("view", "best").param("categoryId", String.valueOf(categoryId))
                        .with(authentication(asRealUser())))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .model().attribute("period", "all"));
    }

    /**
     * 명예의 전당은 정원을 넘지 않는다.
     *
     * <p>기간 조건이 없어 정원을 안 두면 게시판 전체가 들어온다. 글이 늘수록
     * 페이지가 계속 늘어 조회수 한 자릿수 글까지 전당에 오른다.
     */
    @Test
    void 명예의_전당은_정원을_넘지_않는다() throws Exception {
        var result = mockMvc.perform(get("/community/post-list")
                        .param("view", "best").param("period", "all")
                        .with(authentication(asRealUser())))
                .andExpect(status().isOk())
                .andReturn();

        var data = (com.example.demo.community.dto.CommunityDto)
                result.getModelAndView().getModel().get("data");
        int capacity = communityProperties.getHallOfFameSize();

        // 게시판에 정원보다 글이 많아야 정원이 걸렸는지 알 수 있다.
        // 적으면 상한이 없어도 통과해 버려서, 그때는 확인 자체를 건너뛴다.
        long onBoard = postRepository.count();
        org.junit.jupiter.api.Assumptions.assumeTrue(onBoard > capacity,
                "글이 정원보다 적어 정원 확인을 건너뜁니다");

        org.assertj.core.api.Assertions.assertThat(data.getPosts().getTotalElements())
                .isEqualTo(capacity);
        org.assertj.core.api.Assertions.assertThat(data.getPosts().getTotalPages())
                .isEqualTo(capacity / 10);
    }

    /** 정원 밖 페이지를 직접 찍고 들어와도 조용히 비어야 한다. 주소는 손으로 고칠 수 있다. */
    @Test
    void 전당_정원_밖_페이지는_비어_있다() throws Exception {
        int pastCapacity = communityProperties.getHallOfFameSize() / 10 + 5;
        var result = mockMvc.perform(get("/community/post-list")
                        .param("view", "best").param("period", "all")
                        .param("page", String.valueOf(pastCapacity))
                        .with(authentication(asRealUser())))
                .andExpect(status().isOk())
                .andReturn();

        var data = (com.example.demo.community.dto.CommunityDto)
                result.getModelAndView().getModel().get("data");
        org.assertj.core.api.Assertions.assertThat(data.getPosts().getContent()).isEmpty();
    }

    /** 기간을 바꿔도 전부 그려져야 한다. 하나라도 식이 틀리면 여기서 잡힌다. */
    @Test
    void 베스트_기간별로_모두_그려진다() throws Exception {
        for (String period : new String[]{"daily", "week", "month", "all"}) {
            mockMvc.perform(get("/community/post-list")
                            .param("view", "best").param("period", period)
                            .with(authentication(asRealUser())))
                    .andExpect(status().isOk());
        }
    }

    /** 모르는 값이 와도 기본값으로 떨어져야 한다. 주소를 직접 고쳐 넣는 경우가 있다. */
    @Test
    void 이상한_기간값이_와도_열린다() throws Exception {
        mockMvc.perform(get("/community/post-list")
                        .param("view", "best").param("period", "yesterday")
                        .with(authentication(asRealUser())))
                .andExpect(status().isOk());
    }

    /**
     * 정렬을 없앤 뒤에도 옛 주소가 깨지지 않아야 한다.
     *
     * <p>{@code sort=popular} 링크가 밖에 나가 있을 수 있다. 모르는 파라미터로
     * 흘려보내고 최신순 목록을 주면 된다 — 오류를 내면 그 링크를 누른 사람만 막힌다.
     */
    @Test
    void 없어진_정렬_주소로_들어와도_목록이_그려진다() throws Exception {
        mockMvc.perform(get("/community/post-list").param("sort", "popular")
                        .with(authentication(asRealUser())))
                .andExpect(status().isOk())
                // 정렬 링크가 화면에 남아 있으면 안 된다
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("sort=popular"))));
    }
}
