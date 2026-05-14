package org.ymz.app.oss;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.ymz.app.model.enums.oss.BucketType;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URI;

/**
 * OSS 客户端，基于 S3 兼容协议封装对象存储操作
 *
 * @author ymz
 */
@Slf4j
public class OssClient {
    private final Properties properties;
    private final S3Client s3Client;

    public OssClient(Properties properties) {
        this.properties = properties;

        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider
                .create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        URI endpoint = URI.create(properties.uri());
        // S3 客户端：用于实际上传、删除等对象操作
        this.s3Client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(credentialsProvider)
                .forcePathStyle(true)
                .build();
    }

    /**
     * 上传对象
     *
     * @param bucketType    目标桶类型
     * @param inputStream   对象内容输入流
     * @param key           对象 key
     * @param contentType   对象内容类型
     * @param contentLength 对象内容长度（字节，需与实际内容一致）
     */
    public void uploadObject(BucketType bucketType, InputStream inputStream, String key, String contentType,
            long contentLength) {
        String bucket = resolveBucket(bucketType);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
        } catch (SdkException e) {
            log.error("文件上传失败: bucket={}, key={}", bucket, key, e);
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, e.getMessage(), e);
        }
    }

    /**
     * 删除对象
     *
     * @param bucketType 目标桶类型
     * @param key        对象 key
     */
    public void deleteObject(BucketType bucketType, String key) {
        String bucket = resolveBucket(bucketType);
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.deleteObject(request);
        } catch (S3Exception e) {
            log.error("文件删除失败: bucket={}, key={}", bucket, key, e);
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, e.getMessage(), e);
        }
    }

    /**
     * 获取公有桶对象的访问地址
     *
     * @param key 对象 key
     * @return 对象访问地址
     */
    public String getPublicObjectUrl(String key) {
        return StrUtil.format("{}/{}/{}", properties.uri(), properties.publicBucket(), key);
    }

    private String resolveBucket(BucketType bucketType) {
        return switch (bucketType) {
            case PUBLIC -> properties.publicBucket();
            case PRIVATE -> properties.privateBucket();
        };
    }

    /**
     * OSS 配置，仅服务于 {@link OssClient}
     */
    @ConfigurationProperties(prefix = "oss")
    public record Properties(
            String uri,
            String accessKey,
            String secretKey,
            String publicBucket,
            String privateBucket) {
    }
}
