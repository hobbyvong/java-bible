package com.example.migration;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * 阿里云 OSS 客户端封装
 */
public class AliyunOssClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AliyunOssClient.class);
    
    private final OSS ossClient;
    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    
    public AliyunOssClient(String endpoint, String accessKeyId, String accessKeySecret) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        logger.info("阿里云 OSS 客户端初始化成功，Endpoint: {}", endpoint);
    }
    
    /**
     * 从阿里云 OSS 下载文件流
     * @param bucketName 桶名称
     * @param key 对象键
     * @return 文件输入流
     */
    public InputStream downloadFile(String bucketName, String key) {
        try {
            GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, key);
            OSSObject ossObject = ossClient.getObject(getObjectRequest);
            logger.debug("开始下载文件：{}/{}", bucketName, key);
            return ossObject.getObjectContent();
        } catch (Exception e) {
            logger.error("下载文件失败：{}/{}", bucketName, key, e);
            throw new RuntimeException("下载文件失败：" + bucketName + "/" + key, e);
        }
    }
    
    /**
     * 检查文件是否存在
     * @param bucketName 桶名称
     * @param key 对象键
     * @return 是否存在
     */
    public boolean doesObjectExist(String bucketName, String key) {
        return ossClient.doesObjectExist(bucketName, key);
    }
    
    /**
     * 关闭客户端
     */
    public void close() {
        if (ossClient != null) {
            ossClient.shutdown();
            logger.info("阿里云 OSS 客户端已关闭");
        }
    }
}
