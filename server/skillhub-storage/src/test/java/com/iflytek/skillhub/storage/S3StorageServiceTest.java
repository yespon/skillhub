package com.iflytek.skillhub.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3StorageServiceTest {

    @Test
    void generatePresignedUrlUsesPathStyleWhenConfigured() {
        S3StorageProperties properties = new S3StorageProperties();
        properties.setRegion("us-east-1");
        properties.setAccessKey("minioadmin");
        properties.setSecretKey("minioadmin");
        properties.setBucket("skillhub");
        properties.setPublicEndpoint("http://localhost:9000");
        properties.setForcePathStyle(true);

        S3StorageService service = new S3StorageService(properties);
        S3Presigner presigner = S3StorageService.buildS3Presigner(properties);
        ReflectionTestUtils.setField(service, "s3Presigner", presigner);

        try (presigner) {
            String url = service.generatePresignedUrl(
                    "packages/1/1/bundle.zip",
                    Duration.ofMinutes(10),
                    "transport-planner-20260323.094914.zip");

            assertThat(url).startsWith("http://localhost:9000/skillhub/packages/1/1/bundle.zip");
            assertThat(url).contains("response-content-disposition=attachment%3B%20filename%2A%3DUTF-8%27%27transport-planner-20260323.094914.zip");
        }
    }
}