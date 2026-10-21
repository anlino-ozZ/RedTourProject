# server-business（Java SpringBoot 业务后端）

> 红色文旅智能导览系统业务后端，端口 8000。

## 技术栈

Java 17 · SpringBoot 3.2 · MyBatis · MySQL · Redis · Lombok · Maven

## 工程分层

```
src/main/java/com/redtour/business
├── RedTourBusinessApplication.java   启动类（@MapperScan）
├── common/        统一响应 ApiResponse / 错误码 / 分页
├── config/        Cors / Redis / RestTemplate 配置
├── controller/    控制层（REST 接口）
├── service/       服务层（接口 + impl）
├── mapper/        数据访问层（MyBatis）
├── entity/        业务实体
├── dto/           数据传输对象
├── client/        AiEngineClient(HTTP) / HardwareTcpClient(TCP)
└── exception/     全局异常处理
src/main/resources
├── application.yml / application-dev.yml / application-prod.yml
└── mapper/        MyBatis XML（可选）
```

## 对接说明

- **AI 引擎（HTTP）**：`AiEngineClient` 通过 RestTemplate 调用 `ai-engine`(:8001) 的健康检查、问答、Wiki、STT 与 TTS 接口。
- **本地语音识别**：触摸屏上传至 `POST /api/v1/stt` 的 multipart `audio` 字段，由业务后端代理到 AI 引擎。
- **姿态识别**：触摸屏调用 `POST /api/v1/pose/recognize`，业务后端代理 Base64 图像帧，前端无需直连 AI 引擎。
- **姿态流代理**：触摸屏连接 `WS /api/v1/pose/stream?deviceId=...&scenicAreaId=...`，连续 3 帧高置信度同动作后才推送触发内容。
- **树莓派硬件（TCP）**：`HardwareTcpClient` 通过 TCP 长连接（换行分隔 JSON）与 `hardware-rpi` 通信，地址见 `hardware.tcp.*`。

## 环境要求

JDK 17+（推荐 17，Boot 3.x 基线；21/25 实测也可运行）· Maven 3.8+ · MySQL · Redis。

## 配置（敏感信息不入库）

仓库内的 `src/main/resources/application-dev.yml` **只有占位符**，不含任何密码或内网 IP。
新人拿到源码后，按下面两种方式之一提供自己的值即可，**不要修改并提交版本库里的那个文件**。

### 方式一：本地私有配置文件（推荐）

```bash
cd packages/server-business
cp config/application-dev.example.yml config/application-dev.yml
```

然后编辑 `config/application-dev.yml`，至少填两项：

```yaml
mysql:
  password: "你的 MySQL 密码"     # 必填，为空会报 Access denied
redis:
  host: 你的 Redis 地址           # 本机填 localhost；虚拟机/服务器填对应内网 IP
```

要点：

- `packages/server-business/config/` 已在根 `.gitignore` 中忽略，这个文件永远不会被 commit。
- Spring Boot 默认就会加载**运行目录下**的 `config/` 并覆盖 classpath 中的同名配置，**不需要任何额外 import**。
- **工作目录必须是 `packages/server-business`**：命令行在此目录执行；IDEA 请在 Run/Debug Configuration 里把
  `Working directory` 设为 `$MODULE_DIR$`，否则读不到覆盖文件，会退回占位符导致数据库连接失败。
- 自测方法：私有文件没生效时，日志会出现
  `Access denied for user 'root'@'localhost' (using password: NO)` ——注意最后的 `NO`，说明密码取到了空值，
  通常是忘了建 `config/application-dev.yml`，或 IDEA 的 Working directory 指错了目录。

### 方式二：环境变量

不想建文件的话，直接设环境变量（生产 profile 只认这种方式）：

```bash
MYSQL_HOST MYSQL_PORT MYSQL_DATABASE MYSQL_USERNAME MYSQL_PASSWORD
REDIS_HOST REDIS_PORT REDIS_PASSWORD REDIS_DATABASE
HARDWARE_TCP_HOST HARDWARE_TCP_PORT
```

优先级：环境变量 > `config/application-dev.yml` > `application-dev.yml` 里的默认值。

### 首次跑起来的必做清单

| 步骤 | 命令 / 位置 | 说明 |
| --- | --- | --- |
| 1 | `cp config/application-dev.example.yml config/application-dev.yml` | 填入自己的 MySQL 密码与 Redis 地址 |
| 2 | 建库：`CREATE DATABASE red_tour` | 字符集 `utf8mb4` |
| 3 | 导表：`src/main/resources/db/schema.sql` | 表结构 + 初始数据，库名需与上一步一致 |
| 4 | 起 Redis | 本机 6379，或 Docker：`docker run -p 6379:6379 redis` |
| 5 | `GET /api/v1/health` | `db/redis/ai` 都返回 `ok` 才算通；`hardware unavailable` 属正常（树莓派没连） |

## 启动命令

```bash
# 开发（默认 dev profile）
mvn spring-boot:run

# 打包并运行
mvn clean package
java -jar target/server-business.jar

# 指定生产配置
java -jar target/server-business.jar --spring.profiles.active=prod
```

## 配置

修改 `src/main/resources/application-dev.yml` 中的 MySQL / Redis / 硬件 TCP 地址。

## 健康检查

`GET http://localhost:8000/api/v1/health` → `{code,message,data:{db,redis,ai,hardware}}`

## 智能问答

`POST http://localhost:8000/api/v1/ask` 将请求透传至 AI 引擎，并将结果写入
`ask_log`。触摸屏可额外携带请求头 `X-Device-Id` 标记来源设备；AI 引擎或日志
数据库短暂不可用时仍返回结构完整的降级结果。

`GET http://localhost:8000/api/v1/ask/recommendations?scenicAreaId=<id>` 聚合指定景区
最近 30 天的有效问答记录，动态热门问题优先；不足时依次由景区静态配置和统一离线问题
补足至至少 5 条，最多返回 10 条。热门日志查询失败时仍可返回静态或离线推荐。
