package com.example.demo.user.service;

import com.example.demo.user.dto.LocalUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userService.findLocalUserByLoginId(username)
                .map(LocalUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("아이디 또는 비밀번호를 확인해 주세요."));
    }
}
