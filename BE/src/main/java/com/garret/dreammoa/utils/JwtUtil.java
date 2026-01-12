package com.garret.dreammoa.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtUtil {

    @Value("${spring.jwt.secret}")
    private String JWT_SECRET;

    private Key key;

    private static final long ACCESS_TOKEN_EXPIRE_TIME = 1000L * 60 * 60 * 24; // 24시간
    private static final long REFRESH_TOKEN_EXPIRE_TIME = 1000L * 60 * 60 * 24 * 7; // 1주

    private final RedisTemplate<String, String> redisTemplate;

    public JwtUtil(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
    }

    // ✅ 키 네임스페이스 권장 (충돌 방지)
    private String rtKey(Long userId) {
        return "RT:" + userId;
    }

    // ✅ (선택) 구 RT 재사용(replay) 방지용 블랙리스트 키
    private String rtBlacklistKey(String refreshToken) {
        return "RT:BL:" + sha256Base64(refreshToken);
    }

    private String sha256Base64(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
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

        log.info("✅ [AT 발급 완료] userId: {}, email: {}, role: {}", userId, email, role);
        return token;
    }

    /**
     * ✅ RT 생성 + Redis 저장 + TTL 지정(이미 하고 계시던 부분 유지)
     * - 기존 userId.toString() 키 대신 "RT:{userId}"로 변경 권장
     *   (기존 키를 유지해야 하면 rtKey() 대신 userId.toString() 쓰셔도 됩니다)
     */
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

        // ✅ TTL 포함 저장 (덮어쓰기)
        redisTemplate.opsForValue().set(rtKey(userId), refreshToken, REFRESH_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);

        return refreshToken;
    }

    /**
     * ✅ Refresh Token Rotation
     * - refresh 성공 시 호출:
     *   1) (선택) 구 RT가 이미 사용된 토큰이면 차단
     *   2) 새 RT 발급 + Redis 저장(덮어쓰기) + TTL 재설정
     *   3) (선택) 구 RT 블랙리스트에 등록(남은 만료시간만큼)
     */
    public String rotateRefreshToken(Long userId, String oldRefreshToken,
                                     String email, String name, String nickname, String role) {

        // (선택) 구 RT 재사용 방지
        String blKey = rtBlacklistKey(oldRefreshToken);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blKey))) {
            throw new IllegalStateException("Refresh token already used (replay detected).");
        }

        // ✅ 새 RT 발급 + 저장 + TTL 재설정
        String newRefreshToken = createRefreshToken(userId, email, name, nickname, role);

        // (선택) 구 RT를 블랙리스트에 등록 (남은 만료시간만큼)
        long remainingMs = getRemainingExpirationMillis(oldRefreshToken);
        if (remainingMs > 0) {
            redisTemplate.opsForValue().set(blKey, "1", remainingMs, TimeUnit.MILLISECONDS);
        }

        return newRefreshToken;
    }

    private long getRemainingExpirationMillis(String token) {
        try {
            Date exp = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();
            long remaining = exp.getTime() - System.currentTimeMillis();
            return Math.max(0, remaining);
        } catch (Exception e) {
            return 0;
        }
    }

    public String getRoleFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .get("role", String.class);
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
        // ✅ 저장 키도 동일하게 변경
        String storedToken = redisTemplate.opsForValue().get(rtKey(userId));
        return refreshToken != null && refreshToken.equals(storedToken);
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
