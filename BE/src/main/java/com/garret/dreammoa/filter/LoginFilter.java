package com.garret.dreammoa.filter;

import com.garret.dreammoa.domain.dto.user.CustomUserDetails;
import com.garret.dreammoa.domain.model.UserEntity;
import com.garret.dreammoa.utils.AuthorityUtils;
import com.garret.dreammoa.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    private static final Logger logger = LoggerFactory.getLogger(LoginFilter.class);  // Logger 추가

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public LoginFilter(AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        String username = obtainUsername(request);
        String password = obtainPassword(request);

        logger.info("🟢 [로그인 시도] Username: {}", username);
        if (password == null || password.isEmpty()) {
            logger.warn("⚠️ [경고] 비밀번호가 입력되지 않았습니다.");
        }

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(username, password, null);
        return authenticationManager.authenticate(authToken);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authentication) {
        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
        String email = customUserDetails.getEmail();
        String name = customUserDetails.getName();
        String nickname = customUserDetails.getNickname();
        Long userId = customUserDetails.getId();
        UserEntity.Role role = AuthorityUtils.extractRoleFromAuthorities(customUserDetails.getAuthorities());

        logger.info("✅ [로그인 성공] User ID: {}, Email: {}, Name: {}, Nickname: {}, Role: {}",
                userId, email, name, nickname, role);

        UserEntity user = new UserEntity(
                customUserDetails.getId(),
                customUserDetails.getName(),
                customUserDetails.getNickname(),
                customUserDetails.getEmail(),
                customUserDetails.getPassword(),
                customUserDetails.getCreatedAt(),
                customUserDetails.getLastLogin(),
                role,
                null, null, new ArrayList<>()
        );

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        if (iterator.hasNext()) {
            GrantedAuthority auth = iterator.next();
            logger.info("🔹 [권한 정보] {}", auth.getAuthority());
        }

        // Access Token & Refresh Token 생성
        String accessToken = jwtUtil.createAccessToken(userId, email, name, nickname, role.name());
        String refreshToken = jwtUtil.createRefreshToken(userId, email, name, nickname, role.name());

        logger.info("🔑 [AccessToken 발급] {}", accessToken);
        logger.info("🔄 [RefreshToken 발급] {}", refreshToken);

        // Response 헤더에 토큰 추가
        response.addHeader("Authorization", "Bearer " + accessToken);
        response.addHeader("Refresh-Token", refreshToken);
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) {
        logger.error("❌ [로그인 실패] 원인: {}", failed.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
