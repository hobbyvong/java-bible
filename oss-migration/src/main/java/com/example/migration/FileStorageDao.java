package com.example.migration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库访问层 - 用于读取和更新 file_storage 表
 */
public class FileStorageDao {
    
    private static final Logger logger = LoggerFactory.getLogger(FileStorageDao.class);
    
    private final HikariDataSource dataSource;
    
    public FileStorageDao(HikariConfig config) {
        this.dataSource = new HikariDataSource(config);
    }
    
    /**
     * 分页查询待迁移的文件记录
     * @param offset 偏移量
     * @param limit 每页数量
     * @return 文件存储记录列表
     */
    public List<FileStorage> findPendingFiles(int offset, int limit) {
        String sql = "SELECT id, file_name, storage_key, bucket_name, file_size, file_type, provider, migrate_status, create_time, update_time " +
                     "FROM file_storage " +
                     "WHERE migrate_status = 0 OR migrate_status IS NULL " +
                     "ORDER BY id ASC " +
                     "LIMIT ? OFFSET ?";
        
        List<FileStorage> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    FileStorage fs = new FileStorage();
                    fs.setId(rs.getLong("id"));
                    fs.setFileName(rs.getString("file_name"));
                    fs.setStorageKey(rs.getString("storage_key"));
                    fs.setBucketName(rs.getString("bucket_name"));
                    fs.setFileSize(rs.getLong("file_size"));
                    fs.setFileType(rs.getString("file_type"));
                    fs.setProvider(rs.getString("provider"));
                    fs.setMigrateStatus(rs.getInt("migrate_status"));
                    
                    java.sql.Timestamp createTime = rs.getTimestamp("create_time");
                    if (createTime != null) {
                        fs.setCreateTime(createTime.toLocalDateTime());
                    }
                    
                    java.sql.Timestamp updateTime = rs.getTimestamp("update_time");
                    if (updateTime != null) {
                        fs.setUpdateTime(updateTime.toLocalDateTime());
                    }
                    
                    list.add(fs);
                }
            }
        } catch (SQLException e) {
            logger.error("查询待迁移文件失败", e);
            throw new RuntimeException("查询待迁移文件失败", e);
        }
        
        return list;
    }
    
    /**
     * 更新文件迁移状态
     * @param id 文件 ID
     * @param status 迁移状态
     * @param newStorageKey 新的存储键 (华为云)
     * @param newBucketName 新的桶名称 (可选，如果不同)
     */
    public void updateMigrateStatus(Long id, int status, String newStorageKey, String newBucketName) {
        String sql = "UPDATE file_storage SET migrate_status = ?, storage_key = ?, bucket_name = ?, provider = 'HUAWEI', update_time = NOW() WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, status);
            stmt.setString(2, newStorageKey);
            stmt.setString(3, newBucketName);
            stmt.setLong(4, id);
            
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                logger.warn("未找到要更新的记录，ID: {}", id);
            } else {
                logger.info("更新文件迁移状态成功，ID: {}, 状态：{}", id, status);
            }
        } catch (SQLException e) {
            logger.error("更新迁移状态失败，ID: {}", id, e);
            throw new RuntimeException("更新迁移状态失败", e);
        }
    }
    
    /**
     * 更新迁移失败状态并记录错误信息
     * @param id 文件 ID
     * @param errorMsg 错误信息
     */
    public void updateMigrateFailed(Long id, String errorMsg) {
        String sql = "UPDATE file_storage SET migrate_status = 3, update_time = NOW() WHERE id = ?";
        
        // 注意：如果需要记录错误信息到单独字段，请根据实际表结构调整
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            stmt.executeUpdate();
            
            logger.error("文件迁移失败，ID: {}, 错误：{}", id, errorMsg);
        } catch (SQLException e) {
            logger.error("更新失败状态失败，ID: {}", id, e);
        }
    }
    
    /**
     * 统计待迁移文件数量
     * @return 待迁移文件数量
     */
    public long countPendingFiles() {
        String sql = "SELECT COUNT(*) FROM file_storage WHERE migrate_status = 0 OR migrate_status IS NULL";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.error("统计待迁移文件数量失败", e);
            throw new RuntimeException("统计待迁移文件数量失败", e);
        }
        
        return 0;
    }
    
    /**
     * 关闭数据源
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
