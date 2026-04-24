package com.example.migration;

import com.obs.services.ObsClient;
import com.obs.services.model.PutObjectRequest;
import com.obs.services.model.PutObjectResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * 华为云 OBS 客户端封装
 */
public class HuaweiObsClient {
    
    private static final Logger logger = LoggerFactory.getLogger(HuaweiObsClient.class);
    
    private final ObsClient obsClient;
    private final String endpoint;
    private final String accessKeyId;
    private final String secretAccessKey;
    
    public HuaweiObsClient(String endpoint, String accessKeyId, String secretAccessKey) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.obsClient = new ObsClient(accessKeyId, secretAccessKey, endpoint);
        logger.info("华为云 OBS 客户端初始化成功，Endpoint: {}", endpoint);
    }
    
    /**
     * 上传文件到华为云 OBS
     * @param bucketName 桶名称
     * @param key 对象键
     * @param inputStream 文件输入流
     * @param contentType 内容类型
     * @return 上传结果
     */
    public PutObjectResult uploadFile(String bucketName, String key, InputStream inputStream, String contentType) {
        try {
            PutObjectRequest request = new PutObjectRequest();
            request.setBucketName(bucketName);
            request.setObjectKey(key);
            request.setInput(inputStream);
            
            logger.debug("开始上传文件：{}/{}", bucketName, key);
            PutObjectResult result = obsClient.putObject(request);
            logger.info("上传文件成功：{}/{}, ETag: {}", bucketName, key, result.getEtag());
            return result;
        } catch (Exception e) {
            logger.error("上传文件失败：{}/{}", bucketName, key, e);
            throw new RuntimeException("上传文件失败：" + bucketName + "/" + key, e);
        }
    }
    
    /**
     * 关闭客户端
     */
    public void close() {
        if (obsClient != null) {
            try {
                obsClient.close();
                logger.info("华为云 OBS 客户端已关闭");
            } catch (IOException e) {
                logger.warn("关闭华为云 OBS 客户端时发生异常", e);
            }
        }
    }
}
