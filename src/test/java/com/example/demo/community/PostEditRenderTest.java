package com.example.demo.community;

import com.example.demo.community.entity.Post;
import com.example.demo.community.repository.PostRepository;
import com.example.demo.user.dto.LocalUserDetails;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 글 고치기 화면이 <b>쓴 사람이 쓴 본문을 그대로 담아서</b> 나오는지 본다.
 *
 * <p>본문은 타임리프 속성이 아니라 자바스크립트 변수(EDIT_CONTENT)로 실려 Quill 에디터에
 * 올라간다. 이 자리는 조용히 틀리기 좋다 — {@code th:inline="javascript"} 가 없는
 * {@code <script>} 안에 두면 타임리프가 값을 문자열이 아니라 <b>주석 안에</b> 넣어
 * ({@code /*본문*}{@code / ''}) 변수에는 빈 문자열만 남는다. 화면은 멀쩡히 그려지고
 * 컴파일도 통과하는데 에디터만 비어서 열리고, 그대로 저장하면 원래 본문이 사라진다.
 * 실제로 그렇게 나갔다(2026-09-21).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostEditRenderTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;

    private static final Pattern JS_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    private User someUser() {
        return userRepository.findAll().stream()
                .filter(u -> "USER".equals(u.getRole()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("USER 계정이 없어 확인할 수 없습니다"));
    }

    private Authentication as(User user) {
        return new UsernamePasswordAuthenticationToken(
                new LocalUserDetails(user), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    private Post givenMyPost(User owner, String content) {
        Post post = new Post();
        post.setUser(owner);
        post.setTitle("고치기 확인용 글");
        post.setContent(content);
        post.setCreatedAt(LocalDateTime.now());
        return postRepository.save(post);
    }

    private String openEditPage(Post post, User me) throws Exception {
        return mockMvc.perform(get("/community/post/" + post.getId() + "/edit")
                        .with(authentication(as(me))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * 브라우저가 실제로 읽게 되는 값으로 되돌린다.
     *
     * <p>주석을 걷어내는 것이 핵심이다. 안 걷으면 본문이 주석 안에 갇혀 에디터가 비어 있는
     * 상태에서도 시험이 통과해 버린다. 타임리프 자바스크립트 인라인은 한글을 {@code \\uXXXX}
     * 로 적어 내보내므로 그것도 되돌려야 글자로 비교할 수 있다.
     */
    private static String asBrowserSeesIt(String scriptLine) {
        String withoutComments = JS_COMMENT.matcher(scriptLine).replaceAll("");
        Matcher m = UNICODE_ESCAPE.matcher(withoutComments);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(
                    String.valueOf((char) Integer.parseInt(m.group(1), 16))));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String editContentLine(String html) {
        return html.lines()
                .filter(l -> l.contains("EDIT_CONTENT ="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("EDIT_CONTENT 줄 자체가 화면에 없다"));
    }

    @Test
    @Transactional
    void 고치기_화면에_내가_쓴_본문이_실린다() throws Exception {
        User me = someUser();
        // 실제 글처럼 Quill 이 만드는 HTML 을 넣는다.
        Post post = givenMyPost(me,
                "<p>해운대 새벽 바다가 진짜 좋았어요</p><p>다음엔 꼭 일출 보고 오려고요</p>");

        String line = editContentLine(openEditPage(post, me));
        String assigned = asBrowserSeesIt(line);

        assertThat(assigned)
                .as("고치기 화면의 EDIT_CONTENT 에 쓴 사람의 본문이 실려야 한다.%n"
                        + "  화면에 나간 줄: %s%n  브라우저가 읽는 값: %s", line, assigned)
                .contains("해운대 새벽 바다가 진짜 좋았어요")
                .contains("다음엔 꼭 일출 보고 오려고요");
    }

    @Test
    @Transactional
    void 본문이_주석에_갇히지_않는다() throws Exception {
        // 실제로 났던 고장을 그대로 겨냥한다: 값이 /* ... */ 안에 들어가고 변수엔 '' 만 남았다.
        User me = someUser();
        Post post = givenMyPost(me, "<p>주석에 갇히면 안 되는 본문</p>");

        String line = editContentLine(openEditPage(post, me));

        assertThat(JS_COMMENT.matcher(line).replaceAll("").trim())
                .as("EDIT_CONTENT 가 빈 문자열로 남으면 에디터가 비어서 열린다. 실제 줄: %s", line)
                .isNotEqualTo("const EDIT_CONTENT = '';")
                .isNotEqualTo("const EDIT_CONTENT = \"\";");
    }

    @Test
    @Transactional
    void 고치기_화면에_내가_쓴_제목이_실린다() throws Exception {
        User me = someUser();
        Post post = givenMyPost(me, "<p>본문</p>");

        assertThat(openEditPage(post, me)).contains("고치기 확인용 글");
    }

    @Test
    @Transactional
    void 남의_글은_고치기로_못_들어간다() throws Exception {
        User me = someUser();
        User other = userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("다른 계정이 없어 확인할 수 없습니다"));
        Post theirPost = givenMyPost(other, "<p>남의 글</p>");

        mockMvc.perform(get("/community/post/" + theirPost.getId() + "/edit")
                        .with(authentication(as(me))))
                .andExpect(status().is3xxRedirection());
    }
}
