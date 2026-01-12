package cn.ling.utils;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MinIO对象存储工具类
 * 提供文件上传、下载、删除及存储桶管理等功能
 */
@Slf4j
@Component
public class OssUtils implements InitializingBean {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.accessKey}")
    private String accessKey;

    @Value("${minio.secretKey}")
    private String secretKey;

    @Value("${minio.bucket:default-bucket}")
    private String defaultBucket;

    private MinioClient minioClient;

    @Override
    public void afterPropertiesSet() {
        try {
            log.info("初始化MinIO客户端, endpoint: {}", endpoint);
            minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            createBucketIfNotExists(defaultBucket);
            log.info("MinIO客户端初始化成功");
        } catch (Exception e) {
            log.error("MinIO客户端初始化失败", e);
            throw new RuntimeException("MinIO初始化失败", e);
        }
    }

    /**
     * 上传文件
     *
     * @param inputStream 文件流
     * @param objectName  对象路径（文件名）
     * @param contentType 文件类型
     * @return 完整访问URL
     */
    public String uploadFile(InputStream inputStream, String objectName, String contentType) {
        return uploadFile(inputStream, defaultBucket, objectName, contentType);
    }

    /**
     * 上传文件（自定义存储桶）
     *
     * @param inputStream 文件流
     * @param bucketName  存储桶名称
     * @param objectName  对象路径
     * @param contentType 文件类型
     * @return 完整访问URL
     */
    public String uploadFile(InputStream inputStream, String bucketName, String objectName, String contentType) {
        try {
            // 读取流计算MD5（注意：这会消耗流，需要重置或先读入内存）
            // 为了计算MD5，这里先转为字节数组
            byte[] bytes = inputStream.readAllBytes();
            return uploadFile(bytes, bucketName, objectName, contentType);
        } catch (Exception e) {
            log.error("文件流读取失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 上传文件（字节数组）
     *
     * @param bytes       文件字节数组
     * @param bucketName  存储桶名称
     * @param objectName  对象路径
     * @param contentType 文件类型
     * @return 完整访问URL
     */
    public String uploadFile(byte[] bytes, String bucketName, String objectName, String contentType) {
        try {
            // 计算MD5
            String md5 = DigestUtils.md5DigestAsHex(bytes);
            log.info("文件MD5校验值: {}", md5);

            // 确保Bucket存在
            createBucketIfNotExists(bucketName);

            // 上传
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(bais, bytes.length, -1)
                            .contentType(contentType)
                            .build()
            );
            bais.close();

            // 拼接返回URL
            // 注意：MinIO默认生成的URL可能不带端口或通过Nginx代理，这里按要求拼接完整URL
            // 格式：endpoint/bucket/object
            String fileUrl = String.format("%s/%s/%s", endpoint, bucketName, objectName);
            log.info("文件上传成功: {}", fileUrl);
            return fileUrl;

        } catch (Exception e) {
            log.error("文件上传异常: bucket={}, object={}", bucketName, objectName, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件
     *
     * @param fileUrl 文件完整URL
     * @return 文件流
     */
    public InputStream downloadFile(String fileUrl) {
        try {
            // 解析URL获取Bucket和Object
            // 假设URL格式为 endpoint/bucket/object
            // 简单解析：移除endpoint部分
            String path = fileUrl.replace(endpoint + "/", "");
            int firstSlashIndex = path.indexOf("/");
            if (firstSlashIndex == -1) {
                throw new IllegalArgumentException("无效的文件URL格式");
            }
            String bucketName = path.substring(0, firstSlashIndex);
            String objectName = path.substring(firstSlashIndex + 1);

            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("文件下载失败: url={}", fileUrl, e);
            throw new RuntimeException("文件下载失败", e);
        }
    }
    
    /**
     * 断点续传下载（获取部分流）
     * @param fileUrl 文件URL
     * @param offset 起始字节位置
     * @param length 读取长度（-1表示读到末尾）
     * @return 文件流
     */
     public InputStream downloadFileRange(String fileUrl, long offset, long length) {
        try {
             String path = fileUrl.replace(endpoint + "/", "");
            int firstSlashIndex = path.indexOf("/");
            if (firstSlashIndex == -1) {
                throw new IllegalArgumentException("无效的文件URL格式");
            }
            String bucketName = path.substring(0, firstSlashIndex);
            String objectName = path.substring(firstSlashIndex + 1);

            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .offset(offset)
                            .length(length > 0 ? length : null)
                            .build()
            );
        } catch (Exception e) {
            log.error("文件断点下载失败: url={}, offset={}", fileUrl, offset, e);
             throw new RuntimeException("文件下载失败", e);
        }
     }

    /**
     * 删除文件
     *
     * @param fileUrl 文件完整URL
     * @return 是否成功
     */
    public boolean deleteFile(String fileUrl) {
        try {
            String path = fileUrl.replace(endpoint + "/", "");
            int firstSlashIndex = path.indexOf("/");
            if (firstSlashIndex == -1) {
                return false;
            }
            String bucketName = path.substring(0, firstSlashIndex);
            String objectName = path.substring(firstSlashIndex + 1);

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("文件删除成功: {}", fileUrl);
            return true;
        } catch (Exception e) {
            log.error("文件删除失败: {}", fileUrl, e);
            return false;
        }
    }

    // --- 辅助功能 ---

    /**
     * 创建存储桶
     */
    public void createBucketIfNotExists(String bucketName) {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("创建存储桶成功: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("创建存储桶异常: {}", bucketName, e);
            throw new RuntimeException("存储桶操作失败", e);
        }
    }

    /**
     * 列出所有存储桶
     */
    public List<Bucket> listBuckets() {
        try {
            return minioClient.listBuckets();
        } catch (Exception e) {
            log.error("获取存储桶列表失败", e);
            throw new RuntimeException("获取存储桶列表失败", e);
        }
    }

    /**
     * 删除存储桶
     */
    public void removeBucket(String bucketName) {
        try {
            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.error("删除存储桶失败: {}", bucketName, e);
            throw new RuntimeException("删除存储桶失败", e);
        }
    }

    /**
     * 获取对象元数据
     */
    public StatObjectResponse getObjectMetadata(String bucketName, String objectName) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("获取对象元数据失败", e);
            throw new RuntimeException("获取对象元数据失败", e);
        }
    }

    /**
     * 生成预签名URL（临时访问链接）
     *
     * @param bucketName 存储桶
     * @param objectName 对象名
     * @param duration   有效期
     * @param unit       时间单位
     * @return 预签名URL
     */
    public String getPresignedUrl(String bucketName, String objectName, int duration, TimeUnit unit) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(duration, unit)
                            .build()
            );
        } catch (Exception e) {
            log.error("生成预签名URL失败", e);
            throw new RuntimeException("生成预签名URL失败", e);
        }
    }
}
