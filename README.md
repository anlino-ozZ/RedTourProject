# 红色文旅智能导览系统（red-tour-main）

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
red-tour-main
├── .github/ISSUE_TEMPLATE/   Issue 模板（feat / bug）
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

| 成员 | 负责模块 | 仓库目录 |
| --- | --- | --- |
| 成员1 | 游客 H5 前端 | `packages/web-h5` |
| 成员2 | PC 管理后台（运营商后台） | `packages/web-admin` |
| 成员3 | 触摸屏大屏 + public-common 维护 | `packages/web-touch`、`public-common` |
| 成员4 | 业务后端 + 数据库设计 | `packages/server-business` |
| 成员5 | AI 算法引擎 + 树莓派硬件脚本 | `packages/ai-engine`、`packages/hardware-rpi` |

> 数据库表结构由成员4 统一设计；各成员按需提交表需求并自行编写本人业务 CRUD SQL，不改动他人模块代码。

## 五、Git 分支规则

- `main`：长期分支，仅存放迭代最终合并代码，**禁止直接 push**
- `dev`：长期分支，日常开发集成
- 功能分支：`feat/模块-功能`（如 `feat/admin-景点管理`）
- 修复分支：`bugfix/模块-问题`（如 `bugfix/h5-语音播放卡顿`）
- 模块简称：`h5` / `admin` / `touch` / `server` / `ai` / `rpi` / `common`

标准开发流程：

```bash
# 1. 基于 dev 拉取最新并切功能分支
git checkout dev && git pull origin dev
git checkout -b feat/admin-景点管理

# 2. 开发提交（仅提交本人业务代码与文档）
git add <本人模块文件>
git commit -m "feat(admin): 新增景点管理列表页"

# 3. 推送并发起 PR 到 dev（至少一人 Review）
git push origin feat/admin-景点管理

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

## 七、Issue 规范

Issue 标题强制格式：`【模块】描述`，模块取值：

`【前端H5】【前端Admin】【前端Touch】【后端业务】【AI算法】【硬件Rpi】【通用】`

模板见 `.github/ISSUE_TEMPLATE/feat.md` 与 `bug.md`。

## 八、文档说明

仓库 `docs/` 仅存放**精简开发文档**（代码规范、UI 规范、架构与硬件清单、精简 PRD、接口文档）；
完整业务 PRD、变更记录、风险表存放于**外部腾讯文档**，仓库不重复存储。

## 九、忽略规则

`node_modules`、Python 虚拟环境 `venv`、AI 大模型权重（`.pt/.gguf` 等）、树莓派镜像（`.img`）均已在 `.gitignore` 中忽略，**禁止提交到仓库**。
