# 红色文旅智能导览系统（RedTourProject）

> 青年红色筑梦之旅 · 树莓派5 + Hailo8L 边缘离线 AI 文旅系统
> 单仓库 Monorepo · 三端 Vue3 前端 + Java SpringBoot 业务后端 + Python AI 引擎 + Python 树莓派硬件脚本

---

## 一、项目简介

面向红色景区的**可离线、AI 驱动**智能导览与互动系统。核心特点：

- **离线可用**：乡村弱网/无网环境 100% 可运行，问答响应 ≤ 3 秒
- **LLM Wiki**：将党史/地方志等史料“编译”为结构化互联 Markdown 百科，摄入时合成、知识复利，规避 RAG 切片式幻觉与“知识蒸发”
- **机架工程（Harness）**：LocalContextMiddleware 上下文注入、推理三明治、验证门 + todo.md 协议，确保史实严谨不瞎编
- **CV 姿态互动**：YOLOv8-pose + Hailo8L（13~26 TOPS，30+ FPS）识别敬礼/推磨等动作触发讲解
- **三端统一**：游客 H5、PC 管理后台、触摸屏大屏共享 `public-common` 红色主题与公共组件

## 二、技术栈

| 层 | 技术 |
| --- | --- |
| 前端（三端） | Vue3 + Vite5 + TypeScript + Pinia + VueRouter4 + Axios + Element Plus |
| 公共包 | public-common（红色主题样式、公共组件、TS 类型、工具函数） |
| 业务后端 | Java 17 · SpringBoot 3 · MyBatis · MySQL · Redis · JWT |
| AI 引擎 | Ollama 本地推理 · YOLOv8-pose · Hailo8L · LLM Wiki · 机架工程 |
| 硬件脚本 | 树莓派5 · RPi.GPIO · pyserial · pygame |
| 代码规范 | ESLint + Prettier（前端）/ Maven 规约（Java 后端）/ Ruff（Python AI·硬件） |

## 三、仓库结构

```
RedTourProject
├── .github/workflows/       CI 预留（暂无配置）
├── docs/                     精简开发文档（5 份）
├── scripts/                  一键打包 / 树莓派部署 / 模型同步脚本
├── public-common/            全局公共包（样式 / 组件 / 类型 / 工具）
├── packages/
│   ├── web-h5/               游客移动端 H5（Vue3+Vite+TS）
│   ├── web-admin/            PC 管理后台（Vue3+Vite+TS）
│   ├── web-touch/            触摸屏大屏（Vue3+Vite+TS，超大按钮、无登录）
│   ├── server-business/      Java SpringBoot 业务后端（MySQL/Redis）
│   ├── ai-engine/            Python LLM Wiki / 机架 / YOLOv8 / Ollama
│   └── hardware-rpi/         Python 树莓派外设脚本
├── .eslintrc.js              全局前端代码校验
├── .prettierrc               全局格式化
├── .gitignore                全局忽略文件
├── package.json              根工程（仅全局脚本/规范，无业务依赖）
└── README.md                 本文件
```

## 四、团队分工（5 人）

| 成员 | 角色 | 负责模块 | 仓库目录 |
| --- | --- | --- | --- |
| 钟安琳（项目负责人） | 前端 / PM / AI 算法 | ① PC 管理后台 ② public-common 规范维护 ③ 项目进度统筹 + 需求对接 + 文档版本管理 | `packages/web-admin`、`public-common` |
| 杨淑婷 | 后端 / AI 算法 | ① AI 引擎（Ollama/LLM Wiki/推理编排） ② 后端智能问答/知识库 CRUD 接口 ③ Wiki 编译与导入流程 | `packages/ai-engine`、`packages/server-business`（问答/知识库模块） |
| 马培桦 | 前端 / 硬件 | ① 触摸屏大屏端 ② 树莓派硬件 UI 对接（姿态互动 / 设备状态 / 成就二维码） ③ Chromium kiosk 模式部署与调参 | `packages/web-touch` |
| 杨弋伽 | 后端 / 硬件 | ① Java SpringBoot 业务后端主程（表结构统一设计 + 鉴权/景区/景点/路线/特产/订单/打卡点/成就/统计 全部 CRUD） ② 硬件 TCP 协议对接 + 心跳重连 + 离线数据同步 | `packages/server-business`（除问答/知识库外）、`packages/hardware-rpi` 协议联调 |
| 倪佳音 | 前端 | ① 游客移动端 H5（由钟安琳带：先做静态页面 → 接口对接 → 导览/问答/个人中心 全流程） | `packages/web-h5` |

> **数据库表结构由杨弋伽 统一设计**；各成员按需提交表需求并自行编写本人业务 CRUD SQL，不改动他人模块代码。
> **需求&接口文档变更由钟安琳 统一登记**，成员只提需求不直接改 docs/，避免多人改同一文档冲突。

## 五、Git 分支规则

- `main`：长期分支，仅存放迭代最终合并代码，**禁止直接 push**
- `dev`：长期分支，日常开发集成
- 功能分支：`feat/模块-功能-年月日-名字缩写`（如 `feat/Admin-ScenicManagement-20260809-ZAL`）
- 修复分支：`bugfix/模块-功能-年月日-名字缩写`（如 `bugfix/H5-AudioPlaybackStuck-20260810-NJY`）
- 分支名**全英文 + 大驼峰**，禁止中文；模块名首字母大写：`H5` / `Admin` / `Touch` / `Server` / `Ai` / `Rpi` / `Common`
- 命名格式：`<类型>/<模块>-<功能描述>-<YYYYMMDD>-<姓名首字母大写缩写>`，功能描述用大驼峰，单词间不加连字符
- 5 人名字缩写：`ZAL`（钟安琳）/ `YST`（杨淑婷）/ `MPH`（马培桦）/ `YYJ`（杨弋伽）/ `NJY`（倪佳音）

标准开发流程：

```bash
# 1. 基于 dev 拉取最新并切功能分支
git checkout dev && git pull origin dev
git checkout -b feat/Admin-ScenicManagement-20260809-ZAL

# 2. 开发提交（仅提交本人业务代码与文档）
git add <本人模块文件>
git commit -m "feat(admin): 新增景点管理列表页"

# 3. 推送并发起 PR 到 dev（至少一人 Review）
git push origin feat/Admin-ScenicManagement-20260809-ZAL

# 4. 迭代验收通过后，由负责人将 dev 合并至 main 并打 tag
git checkout main && git merge dev && git tag v1.0.0
```

## 六、各模块启动命令

### 前端（进入对应目录后执行，各自独立依赖）

```bash
# 游客 H5
cd packages/web-h5 && npm install && npm run dev      # 默认 http://localhost:5173

# PC 管理后台
cd packages/web-admin && npm install && npm run dev   # 默认 http://localhost:5174

# 触摸屏大屏
cd packages/web-touch && npm install && npm run dev   # 默认 http://localhost:5175
```

### 业务后端（Java · SpringBoot）

```bash
cd packages/server-business
mvn spring-boot:run                    # 开发运行，默认 :8000，dev profile

# 或打包后运行
mvn clean package
java -jar target/server-business.jar --spring.profiles.active=prod
```

### AI / 硬件（Python，各自虚拟环境）

```bash
# AI 引擎
cd packages/ai-engine
python -m venv venv && venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8001

# 树莓派硬件脚本（仅树莓派环境）
cd packages/hardware-rpi
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python main.py
```

## 七、任务管理

团队通过 **微信群沟通 + 群共享文档** 管理任务分配与进度，不使用 GitHub Issue。
需求变更、接口调整、数据库改动等由钟安琳统一登记到共享文档，成员按文档认领任务。

## 八、文档说明

仓库 `docs/` 存放开发文档（代码规范、UI 规范、架构与硬件清单、PRD 需求文档、接口文档）；
完整业务 PRD 变更记录、风险表存放于**外部腾讯文档**，仓库不重复存储。（暂时还没有这两个表）

## 九、忽略规则

`node_modules`、Python 虚拟环境 `venv`、AI 大模型权重（`.pt/.gguf` 等）、树莓派镜像（`.img`）均已在 `.gitignore` 中忽略，**禁止提交到仓库**。
