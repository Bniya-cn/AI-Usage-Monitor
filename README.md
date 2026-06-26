# AI Usage Monitor (AI 用量监控系统)

> [!IMPORTANT]
> **人机协作开发声明与分工 (AI-Human Collaboration)**
> 本项目由个人与 AI 编程工具协同重构并开发完成，双方的开发分工如下：
> - **AI 编程助手（负责后端主力）**：负责项目后端核心业务逻辑的实现与重构。包括基于 Java Servlet 编写的数据采集服务、定时同步调度器（`SyncScheduler`）、基于 JDBC + MySQL + HikariCP 连接池的数据持久化层、本地 CSV/ZIP 日志解析组件以及跨域过滤器（CORS）等。
> - **个人（负责前端设计与整体需求分析）**：负责系统整体功能规划、需求分析、数据模型设计，以及前端高质感交互界面的视觉设计与实现。包括基于 Vue 3 + Vite 搭建的前端响应式架构，使用原生 CSS 编写的具有毛玻璃质感（Glassmorphism）和精致过渡动效的现代深色主题，利用 ECharts 绘制的动态图表组件，以及前后端接口的对接与联合调试。

本项目是一款现代化的 Web 端应用，用于统一监控、分析和可视化您在多个 AI 服务上的 API 用量——支持 ChatGPT、Claude、Kimi（月之暗面）、GLM（智谱）等接口的自动同步，以及 DeepSeek 历史账单的本地导入解析。

---

## 🌟 系统特色与功能特性

- **多服务 API 监控**：一站式追踪 ChatGPT API、Claude API、Kimi API、GLM API 的 Token 消耗、请求次数和账单费用。
- **DeepSeek 账单导入**：后端集成 CSV 及其 ZIP 压缩包解析器，支持一键上传并解析 DeepSeek 的历史账单数据。
- **实时数据仪表盘**：直观展示每个服务的连接状态、最近同步时间、累计 Token 消费及预估费用。
- **可视化走势图表**：由个人精心设计的 ECharts 走势图，支持 30 天用量历史趋势的切换与交互式对比。
- **自动定时同步**：后端配置了基于监听器（`ServletContextListener`）与后台并发线程的自动调度器，周期性获取各服务最新数据。
- **现代化精致 UI**：前端遵循高质感视觉设计，支持全响应式布局，适配多终端屏幕尺寸，带有平滑的悬浮交互与过渡动画。

---

## 🛠️ 项目技术栈 (Tech Stack)

### 前端 (Frontend)
- **Vue 3**：核心框架（基于 Composition API）
- **Vite**：现代化前端构建与开发工具
- **ECharts**：用于绘制高交互性的用量历史走势图表
- **Axios**：异步网络请求库，与后端 Servlet 接口高效通信
- **Vanilla CSS**：原生现代 CSS 系统，手写精致深色毛玻璃视觉风格

### 后端 (Backend)
- **Java 17**：开发语言版本
- **Java Servlet API 3.1**：轻量级后端 Web 服务框架
- **HikariCP 4.0.3**：高性能数据库连接池，管理与优化 MySQL 数据库连接
- **Gson 2.10.1**：谷歌 JSON 解析处理库
- **MySQL Connector Java 8.0.33**：MySQL 数据库驱动
- **Maven**：后端项目依赖管理与 WAR 包构建工具

### 数据库 (Database)
- **MySQL 8.0**：持久化数据存储，记录 API 密钥、用量快照及认证状态

---

## 📂 项目结构 (Project Structure)

```text
AI-Usage-Monitor/
├── backend/                        # 后端 Maven 项目
│   ├── init.sql                    # 数据库初始化脚本
│   ├── pom.xml                     # Maven 配置文件
│   └── src/
│       └── main/
│           ├── java/com/monitor/   # 后端 Java 源码
│           │   ├── dao/            # 数据访问层 (JDBC)
│           │   │   ├── DBUtils.java        # HikariCP 连接池工具类
│           │   │   └── UsageDao.java       # 用量与配置的 CRUD 操作
│           │   ├── model/          # 实体类 (POJO)
│           │   │   └── UsageRecord.java    # 用量记录模型
│           │   ├── servlet/        # 控制层 (控制器接口)
│           │   │   ├── AuthServlet.java    # 密钥与认证状态 Servlet
│           │   │   ├── ConfigServlet.java  # 全局系统配置 Servlet
│           │   │   └── UsageServlet.java   # 用量汇总、历史获取及 CSV 导入 Servlet
│           │   ├── CharacterEncodingFilter.java # 字符编码过滤器
│           │   ├── CorsFilter.java              # 跨域资源共享过滤器
│           │   ├── CollectorService.java        # 各大 AI 接口同步采集核心服务
│           │   ├── DeepSeekCsvParser.java       # DeepSeek 账单解析器
│           │   ├── MonitorServices.java         # 服务常量定义
│           │   └── SyncScheduler.java           # 后台定时拉取调度器 (Listener)
│           └── webapp/             # Web 根目录（前端打包后的静态资源会自动输出至此）
│               └── WEB-INF/
│                   └── web.xml # Servlet 路由与监听器配置文件
└── frontend/                       # 前端 Vue 3 项目
    ├── index.html                  # 页面主入口
    ├── package.json                # 前端依赖配置
    ├── vite.config.js              # Vite 配置文件（配置了前端代理与打包输出重定向）
    └── src/
        ├── main.js                 # 前端应用入口
        ├── App.vue                 # 根组件
        ├── assets/                 # 静态资源与全局样式
        ├── utils/                  # 前端工具类
        │   ├── api.js              # 基于 Axios 的 API 请求封装
        │   └── chartData.js        # ECharts 图表数据处理助手
        └── components/             # 前端重构组件 (由个人设计与实现)
            ├── Dashboard.vue       # 主仪表盘看板
            ├── ServiceCard.vue     # 单个 AI 服务用量卡片
            ├── UsageChart.vue      # ECharts 可视化图表组件
            └── SettingsModal.vue   # API 密钥与同步周期设置弹窗
```

---

## 🚀 快速启动与部署 (Getting Started)

### 1. 数据库准备
1. 确保已安装并运行 MySQL 8.0 数据库。
2. 创建名为 `ai_usage_monitor` 的数据库，并导入数据表结构：
   ```bash
   mysql -u root -p -e "CREATE DATABASE ai_usage_monitor;"
   mysql -u root -p ai_usage_monitor < backend/init.sql
   ```
3. 如需修改数据库连接密码或端口，请打开 [DBUtils.java](file:///Users/daijinglin/Desktop/202326202099-%E6%88%B4%E7%92%9F%E7%B2%BC/AI-Usage-Monitor/backend/src/main/java/com/monitor/dao/DBUtils.java#L16-L18)，修改 `HikariConfig` 的连接配置：
   ```java
   config.setJdbcUrl("jdbc:mysql://localhost:3306/ai_usage_monitor?...");
   config.setUsername("your_username");
   config.setPassword("your_password");
   ```

### 2. 后端服务启动
1. 进入 `backend` 目录。
2. 使用 Maven 构建项目并在集成的 Tomcat 插件中启动服务：
   ```bash
   cd backend
   mvn tomcat7:run
   ```
3. 后端服务将默认在 `http://localhost:8080` 启动运行。

### 3. 前端开发环境运行
1. 新开命令行终端，进入 `frontend` 目录。
2. 安装前端项目所需依赖项：
   ```bash
   cd frontend
   npm install
   ```
3. 运行前端 Vite 热更新开发服务器：
   ```bash
   npm run dev
   ```
4. 终端会输出本地访问地址，通常为 `http://localhost:5173`。在开发模式下，Vite 会根据 [vite.config.js](file:///Users/daijinglin/Desktop/202326202099-%E6%88%B4%E7%92%9F%E7%B2%BC/AI-Usage-Monitor/frontend/vite.config.js#L17-L21) 将前端对 `/api/*` 的所有请求自动代理转发到后端 `http://localhost:8080/api/*`，从而解决跨域问题。

### 4. 生产打包与统合部署
本项目的构建流程已经过统合优化。当系统准备发布或统一部署时，只需：
1. 在 `frontend` 目录下执行前端打包：
   ```bash
   npm run build
   ```
   *打包后的静态文件（HTML、JS、CSS、资源文件）会自动编译并输出至后端的 `backend/src/main/webapp/` 目录中。*
2. 进入 `backend` 目录，通过 Maven 打包成 WAR 文件：
   ```bash
   cd ../backend
   mvn clean package
   ```
3. 构建完成后，您将在 `backend/target/` 下获得名为 `ai-usage-monitor.war` 的包。将其放入任意 Tomcat 容器的 `webapps` 目录，即可实现前后端合并部署与运行。

---

## 🔒 隐私与安全性

- 所有服务 API 密钥均安全保存在您本地配置的 MySQL 数据库的 `api_keys` 表中。
- 后端数据拉取均从您的服务器/本地直接请求对应 AI 官方 API 接口，不经过任何第三方中转，保证 API 密钥及用量数据的绝对私密。

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源。
