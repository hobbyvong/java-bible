# OSS 迁移工具使用说明

## 项目概述

本工具用于将文件数据从阿里云对象存储 (OSS) 迁移到华为云对象存储 (OBS)，同时更新 MySQL 数据库中的文件存储记录。

## 功能特性

- ✅ 支持批量迁移文件
- ✅ 支持并发处理，提高迁移效率
- ✅ 自动更新数据库中的存储信息
- ✅ 详细的日志记录和统计信息
- ✅ 失败重试机制（通过迁移状态标识）
- ✅ 支持断点续传（通过 migrate_status 字段）

## 前置条件

1. **Java 环境**: JDK 8 或更高版本
2. **Maven**: Maven 3.6+ 
3. **MySQL 数据库**: 包含 `file_storage` 表
4. **阿里云 OSS**: 源文件存储
5. **华为云 OBS**: 目标文件存储

## 数据库表结构

确保你的数据库中有一个 `file_storage` 表，建议结构如下：

```sql
CREATE TABLE `file_storage` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `file_name` varchar(255) DEFAULT NULL COMMENT '文件名称',
  `storage_key` varchar(512) DEFAULT NULL COMMENT '文件在对象存储中的路径/键',
  `bucket_name` varchar(128) DEFAULT NULL COMMENT '桶名称',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小 (字节)',
  `file_type` varchar(128) DEFAULT NULL COMMENT '文件类型/MIME 类型',
  `provider` varchar(32) DEFAULT NULL COMMENT '存储提供商 (ALIYUN/HUAWEI)',
  `migrate_status` tinyint(4) DEFAULT '0' COMMENT '迁移状态 (0:未迁移，1:迁移中，2:迁移成功，3:迁移失败)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_migrate_status` (`migrate_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件存储记录表';
```

## 快速开始

### 1. 编译项目

```bash
cd /workspace/oss-migration
mvn clean package -DskipTests
```

编译完成后，会在 `target` 目录下生成两个 jar 包：
- `oss-migration-1.0.0.jar` - 普通 jar 包
- `oss-migration-1.0.0-jar-with-dependencies.jar` - 包含所有依赖的可执行 jar 包

### 2. 配置参数

复制配置文件模板并修改：

```bash
cp src/main/resources/application.properties.template application.properties
```

编辑 `application.properties` 文件，填入你的配置信息：

```properties
# 数据库配置
db.url=jdbc:mysql://localhost:3306/your_database_name?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
db.username=your_db_username
db.password=your_db_password

# 阿里云 OSS 配置
aliyun.endpoint=oss-cn-hangzhou.aliyuncs.com
aliyun.accessKeyId=your_aliyun_access_key_id
aliyun.accessKeySecret=your_aliyun_access_key_secret

# 华为云 OBS 配置
huawei.endpoint=obs.cn-north-4.myhuaweicloud.com
huawei.accessKeyId=your_huawei_access_key_id
huawei.secretAccessKey=your_huawei_secret_access_key
huawei.bucketName=your-target-bucket-name
```

### 3. 运行迁移程序

**方式一：使用可执行 jar 包**

```bash
java -jar target/oss-migration-1.0.0-jar-with-dependencies.jar
```

**方式二：使用 Maven 运行**

```bash
mvn exec:java -Dexec.mainClass="com.example.migration.OssMigrationApplication"
```

**方式三：指定配置文件路径**

将 `application.properties` 放在以下任一位置：
- 程序运行目录 (`./application.properties`)
- 用户主目录 (`~/oss-migration/application.properties`)
- classpath (`src/main/resources/application.properties`)

## 配置说明

### 必填配置项

| 配置项 | 说明 | 示例 |
|--------|------|------|
| db.url | MySQL 数据库 JDBC URL | jdbc:mysql://localhost:3306/mydb |
| db.username | 数据库用户名 | root |
| db.password | 数据库密码 | password123 |
| aliyun.endpoint | 阿里云 OSS Endpoint | oss-cn-hangzhou.aliyuncs.com |
| aliyun.accessKeyId | 阿里云 AccessKey ID | LTAI5t... |
| aliyun.accessKeySecret | 阿里云 AccessKey Secret | xxxxxxxxxxx |
| huawei.endpoint | 华为云 OBS Endpoint | obs.cn-north-4.myhuaweicloud.com |
| huawei.accessKeyId | 华为云 AK | xxxxxxxxxxx |
| huawei.secretAccessKey | 华为云 SK | xxxxxxxxxxx |
| huawei.bucketName | 华为云目标桶名称 | my-target-bucket |

### 可选配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| migration.batch.size | 100 | 每批处理的文件数量 |
| migration.thread.pool.size | 5 | 并发线程数 |

## 迁移流程

1. **查询待迁移文件**: 从数据库查询 `migrate_status = 0` 或 `NULL` 的记录
2. **下载文件**: 从阿里云 OSS 下载文件到内存流
3. **上传文件**: 将文件流上传到华为云 OBS
4. **更新状态**: 更新数据库记录，设置 `migrate_status = 2`，更新 `storage_key` 和 `bucket_name`
5. **失败处理**: 如果失败，设置 `migrate_status = 3`，可重新运行程序继续迁移

## 迁移状态说明

| 状态码 | 说明 |
|--------|------|
| 0 | 未迁移 |
| 1 | 迁移中 |
| 2 | 迁移成功 |
| 3 | 迁移失败 |

## 监控和日志

- **控制台日志**: 实时输出迁移进度和统计信息
- **文件日志**: 保存在 `logs/oss-migration.log`，按天滚动
- **进度查看**: 每处理 100 个文件输出一次进度统计

## 常见问题

### Q1: 迁移中断后如何继续？
A: 程序会自动跳过已迁移成功的文件（`migrate_status = 2`），只需重新运行即可。

### Q2: 如何调整并发度？
A: 修改代码中的 `THREAD_POOL_SIZE` 常量，或在配置文件中添加 `migration.thread.pool.size`。

### Q3: 迁移速度慢怎么办？
A: 
- 增加并发线程数
- 检查网络带宽
- 确认阿里云和华为云的地域选择（建议选择相近地域）

### Q4: 如何处理大文件？
A: 当前版本使用流式传输，理论上支持任意大小的文件。如需优化，可增加 JVM 堆内存：
```bash
java -Xms2g -Xmx4g -jar target/oss-migration-1.0.0-jar-with-dependencies.jar
```

## 安全建议

1. **AccessKey 安全**: 不要将 AccessKey 提交到代码仓库
2. **最小权限原则**: 为迁移任务创建专用的 RAM 用户/IAM 用户，只授予必要的权限
3. **网络隔离**: 如果在云服务器上运行，确保安全组配置正确
4. **日志脱敏**: 生产环境注意日志中不要泄露敏感信息

## 阿里云和华为云 Endpoint 参考

### 阿里云 OSS 常见 Endpoint
- 华东 1 (杭州): oss-cn-hangzhou.aliyuncs.com
- 华东 2 (上海): oss-cn-shanghai.aliyuncs.com
- 华北 1 (青岛): oss-cn-qingdao.aliyuncs.com
- 华北 2 (北京): oss-cn-beijing.aliyuncs.com
- 华南 1 (深圳): oss-cn-shenzhen.aliyuncs.com

### 华为云 OBS 常见 Endpoint
- 华北 - 北京四：obs.cn-north-4.myhuaweicloud.com
- 华东 - 上海一：obs.cn-east-3.myhuaweicloud.com
- 华南 - 广州：obs.cn-south-1.myhuaweicloud.com
- 西南 - 成都一：obs.cn-southwest-2.myhuaweicloud.com

## 技术支持

如有问题，请查看日志文件或联系开发团队。
