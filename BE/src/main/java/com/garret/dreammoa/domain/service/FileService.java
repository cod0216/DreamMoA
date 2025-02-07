package com.garret.dreammoa.domain.service;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.garret.dreammoa.domain.model.FileEntity;
import com.garret.dreammoa.domain.model.FileEntity.RelatedType;
import com.garret.dreammoa.domain.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final AmazonS3Client amazonS3Client;

    //로컬 파일 저장 경로(게시판 등 다른 파일은 여전히 로컬에 저장)
    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    /**
     * S3에 모든 파일을 저장하는 메서드
     */
    public FileEntity saveFile(MultipartFile multipartFile, Long relatedId, RelatedType relatedType) throws Exception {

        // 파일 크기 제한 조건
        if (relatedType == RelatedType.PROFILE) {
            long maxProfileSize = 2 * 1024 * 1024; // 2MB
            if (multipartFile.getSize() > maxProfileSize) {
                throw new IllegalArgumentException("프로필 사진은 최대 2MB 까지 업로드할 수 있습니다.");
            }
        } else {
            long maxFileSize = 500L * 1024 * 1024; // 500MB
            if (multipartFile.getSize() > maxFileSize) {
                throw new IllegalArgumentException("파일은 최대 500MB 까지 업로드할 수 있습니다.");
            }
        }

        // 관련 타입에 따라 S3 내 저장 폴더 결정
        String folder;
        switch (relatedType) {
            case PROFILE:
                folder = "profile/";
                break;
            case POST:
                folder = "post/";
                break;
            case CHALLENGE:
                folder = "challenge/";
                break;
            default:
                folder = "files/";
        }

        // 고유한 파일 이름 생성 (폴더 + 타임스탬프 + UUID + 확장자)
        String originalFileName = multipartFile.getOriginalFilename();
        String fileExtension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        String fileName = folder + System.currentTimeMillis() + "_" + UUID.randomUUID() + fileExtension;

        // S3 업로드를 위한 메타데이터 설정
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(multipartFile.getSize());
        metadata.setContentType(multipartFile.getContentType());

        // S3에 파일 업로드
        amazonS3Client.putObject(bucketName, fileName, multipartFile.getInputStream(), metadata);

        // S3에 업로드된 파일 URL 생성
        String fileUrl = amazonS3Client.getUrl(bucketName, fileName).toString();

        if(relatedType == RelatedType.PROFILE) {
            Optional<FileEntity> existingOpt = fileRepository
                    .findByRelatedIdAndRelatedType(relatedId, relatedType)
                    .stream().findFirst();
            if(existingOpt.isPresent()){
                FileEntity existing = existingOpt.get();
                //S3에 저장된 기존 파일 삭제
                amazonS3Client.deleteObject(bucketName, existing.getFilePath());
                //기존 레코드를 업데이트
                existing.setFileName(originalFileName);
                existing.setFilePath(fileName);
                existing.setFileUrl(fileUrl);
                existing.setFileType(multipartFile.getContentType());
                return fileRepository.save(existing);
            }
        }

        // FileEntity 생성 및 DB 저장
        FileEntity fileEntity = FileEntity.builder()
                .relatedId(relatedId)
                .relatedType(relatedType)
                .fileName(originalFileName)
                .filePath(fileName)    // S3의 파일 키
                .fileUrl(fileUrl)
                .fileType(multipartFile.getContentType())
                .build();

        return fileRepository.save(fileEntity);
    }

    public List<FileEntity> getFiles(Long relatedId, RelatedType relatedType) {
        return fileRepository.findByRelatedIdAndRelatedType(relatedId, relatedType);
    }

    /**
     * S3에서 파일 삭제
     */
    public void deleteFile(Long fileId) {
        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));
        amazonS3Client.deleteObject(bucketName, fileEntity.getFilePath());
        fileRepository.delete(fileEntity);
    }

    public Optional<FileEntity> getProfilePicture(Long userId) {
        return fileRepository.findByRelatedIdAndRelatedType(userId, RelatedType.PROFILE)
                .stream().findFirst();
    }

}