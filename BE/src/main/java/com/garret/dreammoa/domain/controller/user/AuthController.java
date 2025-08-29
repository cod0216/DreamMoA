package com.garret.dreammoa.domain.controller.user;


import com.garret.dreammoa.domain.dto.user.CustomUserDetails;
import com.garret.dreammoa.domain.dto.user.request.LoginRequest;
import com.garret.dreammoa.domain.service.user.UserService;
import com.garret.dreammoa.utils.JwtUtil;
import com.garret.dreammoa.domain.model.UserEntity;
import com.garret.dreammoa.domain.repository.UserRepository;
import com.garret.dreammoa.domain.service.file.FileService;
import com.garret.dreammoa.utils.CookieUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.RedisTemplate;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, BindingResult bindingResult, HttpServletResponse response) {
        Logger logger = LoggerFactory.getLogger(AuthController.class);

        logger.info("🟢 [로그인 요청] Email: {}", request.getEmail());

        if (bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            bindingResult.getFieldErrors().forEach(error -> {
                errors.put(error.getField(), error.getDefaultMessage());
                logger.warn("⚠️ [입력값 오류] Field: {}, Message: {}", error.getField(), error.getDefaultMessage());
            });
            return ResponseEntity.badRequest().body(errors);
        }

        try {
            // 1️⃣ 이메일로 사용자 정보 조회
            UserEntity userEntity = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> {
                        logger.error("❌ [사용자 조회 실패] 이메일이 존재하지 않음: {}", request.getEmail());
                        return new IllegalArgumentException("User not found");
                    });

            logger.info("✅ [사용자 조회 성공] Email: {}", userEntity.getEmail());
            logger.info("🔐 [DB 저장된 비밀번호] {}", userEntity.getPassword());

            // 2️⃣ 비밀번호 비교 (로그 추가)
            logger.info("🔑 [입력한 비밀번호] {}", request.getPassword());

            if (!passwordEncoder.matches(request.getPassword(), userEntity.getPassword())) {
                logger.error("❌ [비밀번호 불일치] 입력한 비밀번호가 다름");
                return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).body("Invalid credentials");
            }

            // 3️⃣ 인증 객체 생성
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword());

            // 4️⃣ Spring Security 인증 실행
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            logger.info("✅ [인증 성공] Email: {}", request.getEmail());

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

            // 5️⃣ JWT 토큰 생성
            String accessToken = jwtUtil.createAccessToken(
                    userDetails.getId(),
                    userDetails.getUsername(),
                    userDetails.getName(),
                    userDetails.getNickname(),
                    userDetails.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.joining(","))
            );
            String refreshToken = jwtUtil.createRefreshToken(
                    userDetails.getId(),
                    userDetails.getUsername(),
                    userDetails.getName(),
                    userDetails.getNickname(),
                    userDetails.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.joining(","))
            );
            logger.info("🔑 [토큰 생성 완료] AccessToken: {}, RefreshToken: {}", accessToken, refreshToken);

            // 6️⃣ 마지막 로그인 업데이트
            userService.updateLastLogin(userDetails.getId());
            logger.info("🕒 [마지막 로그인 업데이트] UserId: {}", userDetails.getId());

            // 7️⃣ 리프레시 토큰을 쿠키에 저장 (RT는 쿠키에만 담아 전송)
            CookieUtil.addCookie(response, "refresh_token", refreshToken, (int) jwtUtil.getRefreshTokenExpirationTime());
            logger.info("🍪 [쿠키 저장] RefreshToken 저장 완료");

            // 8️⃣ 액세스 토큰은 응답 본문에만 담아 전송 (AT는 메시지 바디)
            Map<String, String> tokenResponse = new HashMap<>();
            tokenResponse.put("accessToken", accessToken);

            return ResponseEntity.ok(tokenResponse);

        } catch (Exception e) {
            logger.error("❌ [로그인 실패] 원인: {}", e.getMessage());
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).body("Invalid credentials");
        }
    }



    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            // Authorization 헤더에서 리프레시 토큰 추출 (Bearer 토큰 형식)
            String refreshToken = resolveTokenFromHeader(request);
            if (refreshToken == null) {
                return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                        .body(Map.of("message", "Authorization 헤더에 Refresh Token이 없습니다."));
            }

            // 리프레시 토큰 유효성 검사
            if (!jwtUtil.validateToken(refreshToken)) {
                return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                        .body(Map.of("message", "Invalid refresh token"));
            }

            // 토큰에서 사용자 정보 추출
            Long userId = jwtUtil.getUserIdFromToken(refreshToken);
            String email = jwtUtil.getEmailFromToken(refreshToken);
            String name = jwtUtil.getNameFromToken(refreshToken);
            String nickname = jwtUtil.getNicknameFromToken(refreshToken);
            String role = jwtUtil.getRoleFromToken(refreshToken);

            if (userId == null || email == null) {
                return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                        .body(Map.of("message", "Invalid refresh token payload"));
            }
            // Redis에서 저장된 RT와 비교하여 검증
            if (!jwtUtil.isRefreshTokenValid(userId, refreshToken)) {
                return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                        .body(Map.of("message", "Invalid refresh token"));
            }
            // 새로운 액세스 토큰 생성
            String newAccessToken = jwtUtil.createAccessToken(userId, email, name, nickname, role);
            // 액세스 토큰을 응답 본문에 담아 전송
            return ResponseEntity.ok(Map.of("accessToken", newAccessToken));

        } catch (Exception e) {
            return ResponseEntity.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "서버 내부 오류가 발생했습니다.", "error", e.getMessage()));
        }
    }


    /**
     * Authorization 헤더에서 Bearer 토큰을 추출하는 메서드
     */
    private String resolveTokenFromHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }




    @PostMapping("/user-logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        // 리프레시 토큰 쿠키에서 추출
        String refreshToken = extractTokenFromCookie(request, "refresh_token");

        if (refreshToken != null) {
            String email = jwtUtil.getEmailFromToken(refreshToken);
            // Redis에서 리프레시 토큰 제거
            redisTemplate.delete(email); // Redis에서 해당 토큰 제거

            // 쿠키 삭제
            CookieUtil.deleteCookie(request, response, "access_token");
            CookieUtil.deleteCookie(request, response, "refresh_token");
        }

        return ResponseEntity.ok("Successfully logged out");
    }

    // 쿠키에서 특정 이름의 토큰 값을 추출
    private String extractTokenFromCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
