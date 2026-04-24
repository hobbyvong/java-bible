package com.example.migration;

import lombok.Data;

/**
 * 文件存储记录实体类
 * 对应数据库表 file_storage
 */
@Data
public class FileStorage {
    
    /**
     * 主键 ID
     */
    private Long id;
    
    /**
     * 文件名称
     */
    private String fileName;
    
    /**
     * 文件在对象存储中的路径/键
     */
    private String storageKey;
    
    /**
     * 文件所在桶名称
     */
    private String bucketName;
    
    /**
     * 文件大小 (字节)
     */
    private Long fileSize;
    
    /**
     * 文件类型/MIME 类型
     */
    private String fileType;
    
    /**
     * 存储提供商 (阿里云：ALIYUN, 华为云：HUAWEI)
     */
    private String provider;
    
    /**
     * 迁移状态 (0:未迁移，1:迁移中，2:迁移成功，3:迁移失败)
     */
    private Integer migrateStatus;
    
    /**
     * 创建时间
     */
    private java.time.LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private java.time.LocalDateTime updateTime;
}
