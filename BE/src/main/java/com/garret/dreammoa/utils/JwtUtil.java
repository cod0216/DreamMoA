package com.garret.dreammoa.utils;

import com.garret.dreammoa.domain.model.UserEntity;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {
    @Value("${spring.jwt.secret}")
    private String JWT_SECRET;
    private Key key;
    private static final long ACCESS_TOKEN_EXPIRE_TIME = 1000 * 60 * 60 * 24; // 24시간
    private static final long REFRESH_TOKEN_EXPIRE_TIME = 1000L * 60 * 60 * 24 * 7; // 1주


    private final RedisTemplate<String, String> redisTemplate;

    public JwtUtil(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
    }

    public String createAccessToken(Long userId, String email, String name, String nickname, String role) {
        if (userId == null || email == null || name == null || nickname == null || role == null) {
            log.error("❌ [AT 발급 오류] 필수 정보가 null입니다. userId: {}, email: {}, name: {}, nickname: {}, role: {}",
                    userId, email, name, nickname, role);
            throw new IllegalArgumentException("필수 정보가 null입니다.");
        }

        Date now = new Date();
        Date validity = new Date(now.getTime() + ACCESS_TOKEN_EXPIRE_TIME);

        Map<String, Object> claims = Map.of(
                "userId", String.valueOf(userId),
                "name", name,
                "nickname", nickname,
                "role", role
        );

        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        log.info("✅ [AT 발급 완료] userId: {}, email: {}, role: {}, AT: {}", userId, email, role, token);
        return token;
    }

    public String createRefreshToken(Long userId, String email, String name, String nickname, String role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + REFRESH_TOKEN_EXPIRE_TIME);

        Map<String, Object> claims = Map.of(
                "userId", String.valueOf(userId),
                "name", name,
                "nickname", nickname,
                "role", role
        );

        String refreshToken = Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();


        redisTemplate.opsForValue().set(userId.toString(), refreshToken, REFRESH_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);

        return refreshToken;
    }

    public String getRoleFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .get("role", String.class); // ✅ 역할 정보 가져오기
        } catch (JwtException e) {
            log.error("유효하지 않은 JWT 토큰", e);
            return null;
        }
    }

    public String getEmailFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (JwtException e) {
            log.error("유효하지 않은 JWT 토큰", e);
            return null;
        }
    }

    public String getNameFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .get("name", String.class);
        } catch (JwtException e) {
            log.error("유효하지 않은 JWT 토큰", e);
            return null;
        }
    }

    public String getNicknameFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .get("nickname", String.class);
        } catch (JwtException e) {
            log.error("유효하지 않은 JWT 토큰", e);
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("유효하지 않은 JWT 서명.");
        } catch (ExpiredJwtException e) {
            log.error("만료된 JWT 토큰.");
        } catch (UnsupportedJwtException e) {
            log.error("지원되지 않는 JWT 토큰.");
        } catch (IllegalArgumentException e) {
            log.error("JWT 클레임 문자열이 비어있습니다.");
        }
        return false;
    }

    public boolean isRefreshTokenValid(Long userId, String refreshToken) {
        String storedToken = redisTemplate.opsForValue().get(userId.toString());
        return refreshToken.equals(storedToken);
    }

    public long getAccessTokenExpirationTime() {
        return ACCESS_TOKEN_EXPIRE_TIME / 1000;
    }

    public long getRefreshTokenExpirationTime() {
        return REFRESH_TOKEN_EXPIRE_TIME / 1000;
    }

    public Long getUserIdFromToken(String token) {
        try {
            String userIdStr = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .get("userId", String.class);

            return Long.parseLong(userIdStr);
        } catch (JwtException | NumberFormatException e) {
            log.error("유효하지 않은 JWT 토큰 또는 userId 변환 실패", e);
            return null;
        }
    }
}
