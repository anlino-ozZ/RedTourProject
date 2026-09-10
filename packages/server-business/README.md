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
- **树莓派硬件（TCP）**：`HardwareTcpClient` 通过 TCP 长连接（换行分隔 JSON）与 `hardware-rpi` 通信，地址见 `hardware.tcp.*`。

## 环境要求

JDK 17+、Maven 3.8+、MySQL、Redis。

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
