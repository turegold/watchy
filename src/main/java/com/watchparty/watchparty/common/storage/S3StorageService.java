package com.watchparty.watchparty.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/**
 * S3 접근 공통 서비스.
 * - 업로드: presigned PUT URL을 발급해 클라이언트가 S3에 직접 올림(이미지 바이트가 서버를 안 거침)
 * - 조회: presigned GET URL 발급(버킷 프라이빗 유지)
 * - 삭제: 이미지 교체 시 이전 객체 정리(고아 방지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.presign-expiration-seconds}")
    private long presignExpirationSeconds;

    // 업로드용 presigned PUT URL
    public String createPresignedPutUrl(String key, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignExpirationSeconds))
                .putObjectRequest(putObjectRequest)
                .build();

        String url = s3Presigner.presignPutObject(presignRequest).url().toString();
        log.info("Presigned PUT URL 발급: key={}", key);
        return url;
    }

    // 조회용 presigned GET URL
    public String createPresignedGetUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignExpirationSeconds))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    // 객체 삭제(이미지 교체 시 이전 것 정리). 실패해도 흐름을 막지 않음(best-effort).
    public void deleteObject(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.info("S3 객체 삭제: key={}", key);
        } catch (Exception e) {
            log.warn("S3 객체 삭제 실패(무시): key={}, msg={}", key, e.getMessage());
        }
    }
}
