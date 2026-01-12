package com.garret.dreammoa.config.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garret.dreammoa.config.FileProperties;
import com.garret.dreammoa.domain.model.FileEntity;
import com.garret.dreammoa.domain.model.UserEntity;
import com.garret.dreammoa.domain.repository.FileRepository;
import com.garret.dreammoa.domain.repository.UserRepository;
import com.garret.dreammoa.utils.JwtUtil;
import com.garret.dreammoa.utils.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final FileProperties fileProperties;
    private static final String UPLOAD_DIR = "C:/SSAFY/uploads/profile/";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String registrationId = ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();

        if (registrationId == null) {
            throw new IllegalArgumentException("Missing registrationId in OAuth2 login");
        }

        // 유저 정보 조회 또는 신규 생성
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, oAuth2User.getAttributes());
        Optional<UserEntity> userOptional = userRepository.findByEmail(userInfo.getEmail());

        UserEntity user = userOptional.orElseGet(() -> {
            UserEntity newUser = UserEntity.builder()
                    .email(userInfo.getEmail())
                    .name(userInfo.getName())
                    .nickname(userInfo.getName())
                    .password("SOCIAL_LOGIN")
                    .role(UserEntity.Role.USER)
                    .build();
            return userRepository.save(newUser);
        });

        // 🔹 사용자 정보 업데이트 (이름 변경 시 반영)
        if (!user.getName().equals(userInfo.getName())) {
            user.setName(userInfo.getName());
            userRepository.save(user);
        }

        // 🔹 프로필 이미지 저장
        saveProfileImage(user, userInfo.getProfileImageUrl());

        // 🔹 JWT 토큰 생성
        String accessToken = jwtUtil.createAccessToken(user.getId(), user.getEmail(), user.getName(), user.getNickname(), String.valueOf(user.getRole()));
        String refreshToken = jwtUtil.createRefreshToken(user.getId(), user.getEmail(), user.getName(), user.getNickname(), String.valueOf(user.getRole()));

        // 🔹 쿠키에 토큰 저장
        // ✅ access_token: HttpOnly X (프론트에서 접근 가능)
        CookieUtil.addCookie(response, "access_token", accessToken, (int) jwtUtil.getAccessTokenExpirationTime());

        // ✅ refresh_token: HttpOnly O (보안 강화)
        CookieUtil.addHttpOnlyCookie(response, "refresh_token", refreshToken, (int) jwtUtil.getRefreshTokenExpirationTime());

        // 🔹 프론트엔드 URL로 리디렉트
        response.sendRedirect("http://localhost:5173");
    }
    /**
     * 🔹 프로필 이미지 저장 및 업데이트
     */
    private void saveProfileImage(UserEntity user, String profileImageUrl) {
        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
            try {
                Optional<FileEntity> existingProfile = fileRepository.findByRelatedIdAndRelatedType(user.getId(), FileEntity.RelatedType.PROFILE)
                        .stream()
                        .findFirst();

                String uniqueFileName = UUID.randomUUID().toString() + ".jpg";
                Path filePath = Paths.get(UPLOAD_DIR, uniqueFileName);

                // 디렉토리 생성 (없으면 생성)
                Files.createDirectories(filePath.getParent());

                // 프로필 이미지 다운로드 및 저장
                byte[] imageBytes = new URL(profileImageUrl).openStream().readAllBytes();
                Files.write(filePath, imageBytes);

                String fileUrl = "/uploads/profile/" + uniqueFileName;

                if (existingProfile.isPresent()) {
                    FileEntity profileImage = existingProfile.get();
                    profileImage.setFileName(uniqueFileName);
                    profileImage.setFilePath(filePath.toString());
                    profileImage.setFileUrl(fileUrl);
                    fileRepository.save(profileImage);
                } else {
                    FileEntity newFile = FileEntity.builder()
                            .relatedId(user.getId())
                            .relatedType(FileEntity.RelatedType.PROFILE)
                            .fileName(uniqueFileName)
                            .filePath(filePath.toString())
                            .fileUrl(fileUrl)
                            .fileType("jpeg")
                            .build();
                    fileRepository.save(newFile);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to save profile image", e);
            }
        }
    }
}
