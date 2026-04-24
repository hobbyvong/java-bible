package com.example.migration;

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 迁移任务主程序
 * 
 * 功能：
 * 1. 从 MySQL 数据库读取 file_storage 表中的文件记录
 * 2. 从阿里云 OSS 下载文件
 * 3. 上传到华为云 OBS
 * 4. 更新数据库中的存储信息
 * 
 * 使用方式：
 * 1. 配置 application.properties 文件
 * 2. 运行 main 方法
 * 3. 查看日志输出
 */
public class OssMigrationApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(OssMigrationApplication.class);
    
    // 配置常量
    private static final int BATCH_SIZE = 100;  // 每批处理的文件数量
    private static final int THREAD_POOL_SIZE = 5;  // 并发线程数
    
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("开始执行 OSS 迁移任务");
        logger.info("========================================");
        
        long startTime = System.currentTimeMillis();
        
        // 加载配置
        Properties config = loadConfig();
        if (config == null) {
            logger.error("配置文件加载失败，程序退出");
            return;
        }
        
        // 初始化组件
        FileStorageDao dao = null;
        AliyunOssClient aliyunClient = null;
        HuaweiObsClient huaweiClient = null;
        
        try {
            // 初始化数据库连接池
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(config.getProperty("db.url"));
            hikariConfig.setUsername(config.getProperty("db.username"));
            hikariConfig.setPassword(config.getProperty("db.password"));
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);
            
            dao = new FileStorageDao(hikariConfig);
            
            // 初始化阿里云 OSS 客户端
            String aliyunEndpoint = config.getProperty("aliyun.endpoint");
            String aliyunAccessKeyId = config.getProperty("aliyun.accessKeyId");
            String aliyunAccessKeySecret = config.getProperty("aliyun.accessKeySecret");
            aliyunClient = new AliyunOssClient(aliyunEndpoint, aliyunAccessKeyId, aliyunAccessKeySecret);
            
            // 初始化华为云 OBS 客户端
            String huaweiEndpoint = config.getProperty("huawei.endpoint");
            String huaweiAccessKeyId = config.getProperty("huawei.accessKeyId");
            String huaweiSecretAccessKey = config.getProperty("huawei.secretAccessKey");
            huaweiClient = new HuaweiObsClient(huaweiEndpoint, huaweiAccessKeyId, huaweiSecretAccessKey);
            
            // 目标桶名称
            String targetBucketName = config.getProperty("huawei.bucketName");
            
            logger.info("华为云目标桶名称：{}", targetBucketName);
            
            // 创建迁移服务
            FileMigrationService migrationService = new FileMigrationService(aliyunClient, huaweiClient, dao);
            
            // 统计待迁移文件数量
            long pendingCount = dao.countPendingFiles();
            logger.info("待迁移文件总数：{}", pendingCount);
            
            if (pendingCount == 0) {
                logger.info("没有待迁移的文件，任务结束");
                return;
            }
            
            // 创建线程池进行并发迁移
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            AtomicInteger processedCount = new AtomicInteger(0);
            
            // 分批处理
            int offset = 0;
            while (true) {
                List<FileStorage> files = dao.findPendingFiles(offset, BATCH_SIZE);
                
                if (files == null || files.isEmpty()) {
                    logger.info("所有文件已处理完成");
                    break;
                }
                
                logger.info("本批次处理文件数量：{}, 偏移量：{}", files.size(), offset);
                
                // 提交任务到线程池
                for (FileStorage file : files) {
                    executor.submit(() -> {
                        try {
                            boolean success = migrationService.migrateFile(file, targetBucketName);
                            int count = processedCount.incrementAndGet();
                            
                            if (count % 100 == 0) {
                                logger.info("进度：{}/{}, 成功：{}, 失败：{}, 总字节：{} bytes",
                                          count, pendingCount,
                                          migrationService.getSuccessCount(),
                                          migrationService.getFailCount(),
                                          migrationService.getTotalBytes());
                            }
                        } catch (Exception e) {
                            logger.error("迁移任务执行异常，文件 ID: {}", file.getId(), e);
                            processedCount.incrementAndGet();
                        }
                    });
                }
                
                offset += BATCH_SIZE;
                
                // 可选：每批之间短暂休眠，避免过快请求
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // 关闭线程池并等待完成
            executor.shutdown();
            logger.info("等待所有迁移任务完成...");
            
            if (!executor.awaitTermination(24, TimeUnit.HOURS)) {
                logger.warn("迁移任务未能在 24 小时内完成，强制关闭线程池");
                executor.shutdownNow();
            }
            
            // 输出最终统计
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            logger.info("========================================");
            logger.info("迁移任务完成");
            logger.info("总耗时：{} 秒", duration / 1000);
            logger.info("成功迁移：{} 个文件", migrationService.getSuccessCount());
            logger.info("迁移失败：{} 个文件", migrationService.getFailCount());
            logger.info("迁移总大小：{} bytes ({} MB)", 
                       migrationService.getTotalBytes(),
                       migrationService.getTotalBytes() / (1024 * 1024));
            logger.info("========================================");
            
        } catch (Exception e) {
            logger.error("迁移任务执行异常", e);
        } finally {
            // 关闭资源
            if (aliyunClient != null) {
                aliyunClient.close();
            }
            if (huaweiClient != null) {
                huaweiClient.close();
            }
            if (dao != null) {
                dao.close();
            }
        }
    }
    
    /**
     * 加载配置文件
     */
    private static Properties loadConfig() {
        Properties properties = new Properties();
        
        // 尝试从 classpath 加载
        try (InputStream input = OssMigrationApplication.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            
            if (input != null) {
                properties.load(input);
                logger.info("配置文件加载成功 (classpath:application.properties)");
                return properties;
            }
        } catch (IOException e) {
            logger.warn("从 classpath 加载配置文件失败", e);
        }
        
        // 尝试从当前目录加载
        try (InputStream input = new FileInputStream("application.properties")) {
            properties.load(input);
            logger.info("配置文件加载成功 (./application.properties)");
            return properties;
        } catch (IOException e) {
            logger.warn("从当前目录加载配置文件失败", e);
        }
        
        // 尝试从用户 home 目录加载
        String userHome = System.getProperty("user.home");
        try (InputStream input = new FileInputStream(userHome + "/oss-migration/application.properties")) {
            properties.load(input);
            logger.info("配置文件加载成功 (~/oss-migration/application.properties)");
            return properties;
        } catch (IOException e) {
            logger.warn("从用户 home 目录加载配置文件失败", e);
        }
        
        logger.error("未找到配置文件 application.properties");
        logger.error("请参考 application.properties.template 创建配置文件");
        
        return null;
    }
}
