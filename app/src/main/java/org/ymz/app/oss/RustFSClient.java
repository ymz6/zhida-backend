package org.ymz.app.oss;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.InputStream;
import java.net.URI;

/**
 * RustFS 客户端
 * 封装了标准的 AWS S3 SDK
 * 后续根据业务需要动态增加所需方法
 * @author ymz
 */
@Slf4j
@ConfigurationProperties(prefix = "oss")
public class RustFSClient {
    private final String uri;

    /**
     * 公共桶：存放可直接访问的资源
     */
    private final String publicBucket;

    /**
     * 私有桶：存放需要鉴权访问的资源
     */
    private final String privateBucket;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public RustFSClient(String uri, String accessKey, String secretKey, String publicBucket, String privateBucket) {
        this.publicBucket = publicBucket;
        this.privateBucket = privateBucket;
        this.uri = uri;

        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        URI endpoint = URI.create(uri);
        // S3 客户端：用于实际上传、删除等对象操作
        this.s3Client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(credentialsProvider)
                .forcePathStyle(true)
                .build();

        // 预签名客户端：用于生成带时效性的上传/下载签名链接
        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
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
    public void uploadObject(BucketType bucketType, InputStream inputStream, String key, String contentType, long contentLength) {
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
     * @param key 对象 key
     * @return 对象访问地址
     */
    public String getPublicObjectUrl(String key) {
        return StrUtil.format("{}/{}/{}", uri, publicBucket, key);
    }

    private String resolveBucket(BucketType bucketType) {
        return switch (bucketType) {
            case PUBLIC -> publicBucket;
            case PRIVATE -> privateBucket;
        };
    }
}
