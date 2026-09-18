package com.example.demo.user;

import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 가입이 막히면 안내 화면이 나와야 한다.
 *
 * <p>가입 처리가 한 트랜잭션으로 묶여 있어, 이미 있는 아이디로 가입하면 오류 안내 대신
 * 500 이 났다(UnexpectedRollbackException).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SignupErrorPageTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Test
    void 이미_있는_아이디로_가입하면_안내가_나온다() throws Exception {
        User existing = userRepository.findAll().stream()
                .filter(u -> "local".equals(u.getProvider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("로컬 계정이 없어 확인할 수 없습니다"));

        mockMvc.perform(post("/signup").with(csrf())
                        .param("accountType", "personal")
                        .param("loginId", existing.getProviderId())
                        .param("password", "Check!2345")
                        .param("passwordConfirm", "Check!2345")
                        .param("name", "중복확인")
                        .param("email", "dup-check@example.com")
                        .param("lang", "ja"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/login"))
                .andExpect(model().attributeExists("signupError"));
    }

    @Test
    void 비밀번호_확인이_다르면_안내가_나온다() throws Exception {
        mockMvc.perform(post("/signup").with(csrf())
                        .param("accountType", "personal")
                        .param("loginId", "nobody" + System.nanoTime() % 100000)
                        .param("password", "Check!2345")
                        .param("passwordConfirm", "Other!2345")
                        .param("name", "x")
                        .param("email", "x@example.com"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("signupError"));
    }
}
