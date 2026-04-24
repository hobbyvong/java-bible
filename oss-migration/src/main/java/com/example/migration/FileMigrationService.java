package com.example.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 文件迁移服务 - 核心迁移逻辑
 */
public class FileMigrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileMigrationService.class);
    
    private final AliyunOssClient aliyunOssClient;
    private final HuaweiObsClient huaweiObsClient;
    private final FileStorageDao fileStorageDao;
    
    // 统计信息
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failCount = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    
    public FileMigrationService(AliyunOssClient aliyunOssClient, 
                                HuaweiObsClient huaweiObsClient,
                                FileStorageDao fileStorageDao) {
        this.aliyunOssClient = aliyunOssClient;
        this.huaweiObsClient = huaweiObsClient;
        this.fileStorageDao = fileStorageDao;
    }
    
    /**
     * 迁移单个文件
     * @param fileStorage 文件存储记录
     * @param targetBucketName 目标桶名称 (华为云)
     * @return 是否成功
     */
    public boolean migrateFile(FileStorage fileStorage, String targetBucketName) {
        String sourceBucket = fileStorage.getBucketName();
        String sourceKey = fileStorage.getStorageKey();
        Long fileId = fileStorage.getId();
        
        logger.info("开始迁移文件，ID: {}, 源：{}/{}, 目标：{}/{}", 
                    fileId, sourceBucket, sourceKey, targetBucketName, sourceKey);
        
        InputStream inputStream = null;
        try {
            // 1. 更新状态为迁移中
            fileStorageDao.updateMigrateStatus(fileId, 1, sourceKey, sourceBucket);
            
            // 2. 从阿里云 OSS 下载文件
            inputStream = aliyunOssClient.downloadFile(sourceBucket, sourceKey);
            
            // 3. 上传到华为云 OBS
            huaweiObsClient.uploadFile(targetBucketName, sourceKey, inputStream, fileStorage.getFileType());
            
            // 4. 更新状态为迁移成功
            fileStorageDao.updateMigrateStatus(fileId, 2, sourceKey, targetBucketName);
            
            // 更新统计
            successCount.incrementAndGet();
            if (fileStorage.getFileSize() != null) {
                totalBytes.addAndGet(fileStorage.getFileSize());
            }
            
            logger.info("文件迁移成功，ID: {}, 大小：{} bytes", fileId, fileStorage.getFileSize());
            return true;
            
        } catch (Exception e) {
            logger.error("文件迁移失败，ID: {}", fileId, e);
            
            // 5. 更新状态为迁移失败
            fileStorageDao.updateMigrateFailed(fileId, e.getMessage());
            
            // 更新统计
            failCount.incrementAndGet();
            
            return false;
            
        } finally {
            // 6. 关闭输入流
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    logger.warn("关闭输入流失败", e);
                }
            }
        }
    }
    
    /**
     * 获取成功迁移的文件数量
     */
    public long getSuccessCount() {
        return successCount.get();
    }
    
    /**
     * 获取失败的文件数量
     */
    public long getFailCount() {
        return failCount.get();
    }
    
    /**
     * 获取迁移的总字节数
     */
    public long getTotalBytes() {
        return totalBytes.get();
    }
    
    /**
     * 重置统计信息
     */
    public void resetStats() {
        successCount.set(0);
        failCount.set(0);
        totalBytes.set(0);
    }
}
