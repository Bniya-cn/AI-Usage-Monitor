const fs = require('fs');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel,
  AlignmentType, PageBreak
} = require('docx');

function h1(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun({ text, bold: true, size: 32 })] });
}
function h2(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun({ text, bold: true, size: 28 })] });
}
function h3(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun({ text, bold: true, size: 24 })] });
}
function p(text) {
  return new Paragraph({ spacing: { after: 160 }, children: [new TextRun({ text, size: 22 })] });
}
function qa(q, a) {
  return [
    new Paragraph({ spacing: { before: 200, after: 80 }, children: [new TextRun({ text: `Q：${q}`, bold: true, size: 22 })] }),
    new Paragraph({ spacing: { after: 160 }, children: [new TextRun({ text: `A：${a}`, size: 22 })] })
  ];
}

const qaList = [
  ["项目的研究背景是什么？为什么要做 AI 用量监控？", "随着 ChatGPT、Claude、DeepSeek、Codex 等 AI 工具在个人开发与团队协作中普及，Token 消耗和 API 费用快速增长，但各平台数据分散、口径不一。本项目旨在提供统一的用量看板，帮助用户掌握费用趋势、发现异常消耗，并为成本控制提供数据依据。"],
  ["项目的核心目标是什么？", "核心目标是：① 聚合多 AI 服务商的用量数据；② 提供可视化趋势分析；③ 支持 API 远程拉取与本地日志解析两种采集模式；④ 从桌面版迁移为 H5 Web 版，便于浏览器访问与后续扩展。"],
  ["项目面向哪些用户场景？", "主要面向个人开发者、小团队技术负责人、以及需要管理多个 AI API Key 的用户。典型场景包括：月底对账、发现某项目 Token 异常飙升、对比不同模型使用比例等。"],
  ["你们做了哪些功能？", "支持 8 类服务监控：ChatGPT、Claude、KIMI、GLM、DeepSeek API、Claude Code、Grok Build、Codex。功能包括：服务卡片总览、历史趋势折线图、API Key 校验与保存、定时/手动同步、DeepSeek CSV 导入、本地日志自动采集等。"],
  ["桌面版和 H5 版有什么区别？", "桌面版基于 Python + PyQt6 + SQLite，单机托盘运行；H5 版基于 Vue 3 + Java Servlet + MySQL，通过浏览器访问。H5 版保留了采集器抽象、调度同步、图表分析等核心能力，并扩展了 DeepSeek、Grok、Codex 等本地/混合采集场景。"],
  ["你是如何进行从桌面版到 H5 的迁移的？", "采用「逻辑迁移、分层替换」策略：① 保留 Collector 采集器模式，将 Python 采集逻辑逐一对照改写为 Java；② 将 SQLite 表结构升级为 MySQL，字段语义保持一致；③ 用 Vue 3 组件化重写 PyQt6 界面（Dashboard、ServiceCard、UsageChart、SettingsModal）；④ 前端 build 产物直接输出到 Tomcat 的 webapp 目录，实现前后端一体化部署。"],
  ["迁移过程中遇到的最大挑战是什么？", "最大挑战是各 AI 厂商 API 能力差异大：有的有完整 Usage API（如 OpenAI），有的只能校验 Key（如 Claude/KIMI/GLM），有的只有余额接口（DeepSeek），有的只能读本地日志（Grok/Codex/Claude Code）。需要为每类服务设计不同的采集策略和前端展示逻辑。"],
  ["为什么选择 Web 化而不是继续完善桌面版？", "Web 化有三点优势：① 无需安装，浏览器即可访问；② 后端集中存储，便于多人/多设备查看；③ 与学校实验环境、答辩演示环境兼容性更好。桌面版仍可作为参考实现保留在 ai_usage_monitor/ 目录。"],
  ["系统整体架构是怎样的？", "采用经典三层架构：表现层（Vue 3 SPA）、业务层（Java Servlet + Collector + Scheduler）、数据层（MySQL）。前端通过 Axios 调用 /api/usage、/api/auth、/api/config 等 REST 风格接口；后端 Servlet 负责路由，Collector 负责采集，UsageDao 负责持久化。"],
  ["请画一下（或描述）数据流。", "用户配置 API Key → AuthServlet 校验并写入 api_keys 表 → SyncScheduler 定时触发 Collector → 采集结果 upsert 到 usage_snapshots → 前端 Dashboard 拉取 summary/history → UsageChart 按日聚合后渲染 ECharts 折线图。本地类服务（Claude Code/Grok/Codex）跳过 Key 校验，直接扫描 ~/.claude、~/.grok、~/.codex 目录。"],
  ["为什么采用 Collector 采集器模式？", "各 AI 服务的数据来源和协议差异很大，Collector 模式将「采集」与「存储/展示」解耦。每个服务实现 BaseCollector 抽象类，统一输出 UsageRecord，便于调度器批量执行，也便于新增服务时只增一个 Collector 文件。"],
  ["BaseCollector 设计了哪些统一接口？", "核心接口包括：getServiceId()、getDisplayName()、collect() 返回 List<UsageRecord>，以及 isAvailable() 判断采集前置条件（API Key 是否存在或本地目录是否存在）。这种设计与桌面版 Python 的 BaseCollector 保持一致，降低迁移成本。"],
  ["多凭证（多 API Key）是如何设计与管理的？", "在 api_keys 表中按 service 主键一对一存储各平台 Key；auth_status 表独立记录校验状态、最近检查时间和错误信息。前端 SettingsModal 提供分服务输入、掩码显示、校验并保存、清除密钥功能。保存时 AuthServlet 会即时触发该服务的同步以验证 Key 有效性。"],
  ["为什么不把所有 Key 放在一个 JSON 里？", "分表按服务存储更清晰：① 便于单独更新/清除某个 Key；② 校验失败时错误信息可精确到服务；③ 符合数据库范式，后续可为不同服务增加加密、过期策略等扩展字段。"],
  ["API Key 的安全性如何保障？", "Key 存储在本地 MySQL，不上传第三方；前端输入框默认 password 类型；传输走本地 HTTP（开发环境）或建议生产环境 HTTPS；校验时仅向各厂商官方 API 发起最小化探测请求。答辩演示环境为本地部署，未暴露公网。"],
  ["本地采集器和远程 API 采集器如何区分？", "通过 MonitorServices.LOCAL_COLLECTORS 标识：claude_code、grok_build、codex 属于本地采集，不需要 API Key，直接解析本地 JSONL/日志文件；其余服务需要 Key 并调用远程 API。调度器将两类服务分设不同同步周期。"],
  ["为什么选用 Vue？是 Vue 2 还是 Vue 3？", "本项目选用的是 Vue 3（^3.4），而非 Vue 2。原因：① Composition API 更适合组件逻辑拆分（如 UsageChart 的图表渲染）；② Vite 对 Vue 3 支持更好，构建速度快；③ Vue 3 是现行主流，生态活跃。若老师问到 Vue 2，可说明 Vue 2 已 EOL，新项目选型 Vue 3 更合理。"],
  ["为什么用 Vite 而不是 Webpack？", "Vite 基于 ES Module 原生开发服务器，冷启动和热更新明显快于 Webpack，适合课程项目快速迭代。build 配置简洁，且可指定 outDir 直接输出到 Java webapp，方便一体化部署。"],
  ["为什么选 ECharts 做图表？", "ECharts 支持折线图、双 Y 轴、面积渐变、图例切换等，能满足 Token/费用/请求次数多维度趋势展示。相比 pyqtgraph（桌面版），ECharts 在 Web 端交互性更强，且文档和社区成熟。"],
  ["后端为什么用 Java Servlet 而不是 Spring Boot？", "课程实验与部署环境以 Tomcat 为主，Servlet 足够承载本项目的 API 规模（config/auth/usage 三个端点），依赖更轻、启动更简单。若后续扩展鉴权、微服务，可平滑迁移到 Spring Boot。"],
  ["为什么选 MySQL 而不是 SQLite？", "H5 版面向 Web 部署：① MySQL 支持并发读写，适合后端服务多线程调度写入；② SUM/GROUP BY 等聚合查询性能更好；③ 便于答辩时展示标准关系型数据库设计。桌面版仍用 SQLite 做轻量本地存储，两者按场景选型。"],
  ["数据库表结构如何设计？", "四张核心表：usage_snapshots（用量快照，按 service+model+date 唯一）、api_keys（密钥）、auth_status（授权状态）、config_settings（同步间隔等配置）。extra_json 字段用 JSON 扩展存储余额、会话数等异构数据，避免频繁改表。"],
  ["usage_snapshots 为什么用 upsert 而不是每次 insert？", "同一服务、同一模型、同一天可能多次同步，upsert（先查后插/更新）可避免重复记录，保证每日快照唯一。这与 UNIQUE KEY (service, model, date_str) 约束配合。"],
  ["同步调度是如何实现的？", "SyncScheduler 实现 ServletContextListener，在 Tomcat 启动时初始化 ScheduledExecutorService。API 类服务按用户配置间隔（默认 30 分钟）同步；本地类服务按 Claude Code 间隔（默认 5 分钟）同步。用户也可在前端点击「同步用量」手动触发全量同步。"],
  ["ChatGPT 的用量数据怎么采集？", "调用 OpenAI Organization Usage API，按 completions/embeddings 等类型分桶拉取 30 天数据，解析 input_tokens、output_tokens、num_model_requests、cost 等字段，按日期+模型聚合后写入快照。"],
  ["Claude/KIMI/GLM 为什么只有 Key 校验没有用量？", "这三家在当前实现中仅调用 /models 等轻量接口验证 Key 有效性，官方未提供与 OpenAI 对等的、仅用 API Key 即可拉取的历史账单 API。H5 端对此有明确提示，属于已知限制而非实现遗漏。"],
  ["DeepSeek 为什么需要 CSV 导入？", "DeepSeek 公开 API 仅提供 /user/balance 余额接口，不提供历史 Token/请求统计。平台用量需从 platform.deepseek.com/usage 导出 CSV。系统同时支持余额差值推算消费趋势，CSV 导入后补齐真实 Token 和请求数。"],
  ["Grok Build 的数据从哪里来？", "从本地 ~/.grok/sessions 目录读取，结构为 项目目录/会话ID/summary.json + chat_history.jsonl。summary.json 提供会话数、消息数；按 last_active_at 日期聚合。Grok 日志不含 usage 字段，故 Token 暂为 0，但请求次数可统计。"],
  ["Codex 的数据如何采集？Mac 版和 VS Code 插件都能抓到吗？", "Codex 会话统一存储在 ~/.codex/sessions/年/月/日/rollout-*.jsonl。解析 type=event_msg 且 payload.type=token_count 的事件，提取 input_tokens、output_tokens。session_meta 中的 source 字段可区分 vscode 插件与 Codex Desktop，两者写入同一路径，因此均可采集。"],
  ["Claude Code 本地日志如何解析？", "扫描 ~/.claude/projects 下各项目的 sessions-index.json 和 *.jsonl 文件，提取每日 Token 与请求数，与桌面版 Python 实现逻辑一致。"],
  ["前后端如何联调？", "开发时 Vite dev server（5173）通过 proxy 将 /api 转发到 Tomcat（8080）。生产构建时 npm run build 输出到 backend/src/main/webapp，由 Tomcat 统一托管静态资源和 Servlet API。"],
  ["项目的难点之一「多模型同日聚合」如何解决？", "ChatGPT 等同日可能有多条不同 model 的记录。前端 chartData.js 提供 aggregateByDate()，按 dateStr 合并 tokensIn、tokensOut、requests、costUsd，避免 X 轴日期重复、折线错位。"],
  ["Token 趋势图为什么从柱状图改为折线图？", "折线图更直观展示时间趋势变化，与桌面版 pyqtgraph 折线风格一致，便于用户观察用量是否突增。费用仍可用第二 Y 轴折线叠加显示。"],
  ["如果某个服务同步失败会怎样？", "SyncScheduler 捕获异常后写入 auth_status.error_msg，前端 ServiceCard 显示「无效Key」或错误提示。不影响其他服务同步。用户可修正 Key 后重新校验。"],
  ["项目如何保证可扩展性？", "新增服务只需四步：① 实现 XxxCollector 继承 BaseCollector；② 在 SyncScheduler.collectorMap 注册；③ 在 MonitorServices.ALL 注册；④ 前端 Dashboard 增加服务项。前后端服务列表通过 MonitorServices 统一管理。"],
  ["有没有做错误处理和边界情况？", "有。例如：DeepSeek 余额 API 401 返回友好中文错误；CSV 导入校验文件格式；Grok/Codex 文件解析单行 JSON 失败时 skip 继续；HTTP 403/404 时 ChatGPT 某 usage 类型跳过而非整体失败。"],
  ["项目的创新点在哪里？", "① 统一监控 API 型 + 本地日志型 AI 工具；② DeepSeek 余额差值 + CSV 双轨补齐数据；③ 从桌面到 H5 的 Collector 模式迁移实践；④ 覆盖 Grok Build、Codex 等新兴工具的本地用量分析。"],
  ["项目有哪些不足或局限？", "① Claude/KIMI/GLM 暂无官方历史用量 API；② Grok 本地日志无 Token 明细；③ API Key 本地明文存储，生产需加密；④ 尚未做用户登录与多租户；⑤ 费用为估算值，以各平台账单为准。"],
  ["如何进行测试验证？", "分三层：① Collector 单元测试（GrokCollectorTest、CodexCollectorTest）本地跑 collect() 验证解析；② 接口测试 curl summary/history/sync；③ 前端浏览器端到端验证卡片、图表、设置、CSV 导入流程。"],
  ["答辩时建议演示什么流程？", "建议 5 分钟演示：① 打开 Dashboard 展示多服务卡片；② 设置页配置一个 API Key 并校验；③ 点击同步用量；④ 切换 ChatGPT/DeepSeek/Codex 查看折线图；⑤ DeepSeek 上传 CSV（如有）展示 Token 补齐。"],
  ["为什么选择前后端分离又一体化部署？", "开发期分离便于 Vite 热更新和 Java 独立重启；部署期前端 build 进 webapp，单 WAR 包即可运行，降低答辩环境搭建复杂度，只需 MySQL + Tomcat。"],
  ["HikariCP 连接池的作用是什么？", "后端多线程调度并发写库，连接池复用 MySQL 连接，避免频繁建连开销，设置 maximumPoolSize=15、connectionTimeout 等参数保证稳定性。"],
  ["CORS 和字符编码如何处理？", "CorsFilter 允许前端跨域访问 API（开发调试）；CharacterEncodingFilter 统一 UTF-8，支持中文错误提示和 DeepSeek CNY 等国际化字段。"],
  ["如果老师问「这是不是 AI 写的代码」怎么回答？", "可以坦诚说明：项目最初桌面版与 H5 版均借助 Claude Code/Cursor 等 AI 编程工具辅助开发，本人负责需求分析、架构设计、迁移方案、调试排错（如 grok_build 未入 SERVICES、DeepSeek CSV 方案）和答辩整理。AI 是生产力工具，核心设计决策由本人完成。"],
  ["项目的社会价值或实际意义？", "帮助开发者在 AI 工具爆发期建立「用量可观测性」，避免费用失控；对小团队成本治理有参考价值；同时展示了如何将分散的 AI 服务数据统一到一个可分析的数据面板。"],
  ["后续可以怎么改进？", "① 引入 Spring Boot + JWT 多用户；② API Key AES 加密存储；③ 接入 Claude/KIMI 官方账单导出；④ 邮件/Webhook 超额告警；⑤ Docker 一键部署；⑥ 移动端适配。"],
  ["为什么不用 MongoDB 存用量？", "用量数据是结构化的时间序列（日期、Token 数、费用），关系型模型更自然，SQL 聚合（SUM/按月统计）更直接。MongoDB 适合文档型异构数据，本项目的 extra_json 已覆盖少量扩展字段。"],
  ["Scheduler 为什么用两个定时任务？", "API 远程调用有频率限制和网络开销，默认 30 分钟；本地日志扫描纯文件 IO，可 5 分钟一次保证及时性。分设 apiSyncJob 和 ccSyncJob 互不影响。"],
  ["Vue 组件如何划分？", "Dashboard 负责总览与数据加载；ServiceCard 展示单服务摘要（DeepSeek 有专属余额模板）；UsageChart 封装 ECharts 多视图切换；SettingsModal 管理 Key 与同步配置、DeepSeek CSV 上传。职责单一，便于维护。"],
  ["axios 封装有什么好处？", "api.js 统一 baseURL、timeout、接口路径，组件只调用 api.getSummary() 等方法，避免 URL 硬编码分散，后期换域名或加拦截器更方便。"],
  ["如果数据库宕机怎么办？", "Servlet 层 catch SQLException 并打印日志，接口返回空数据或 500；前端显示「未同步」状态。生产环境应配置 MySQL 主从/备份。课程演示环境建议提前启动 MySQL 并导入 init.sql。"],
  ["项目的 Git 管理方式？", "项目托管于 Git 仓库，前后端同仓。前端 build 产物在 webapp 目录，可选择性 gitignore 或由 CI 构建注入。答辩建议提交源码 + 演示环境说明。"],
  ["如何保证答辩现场稳定？", "提前：① 启动 MySQL 并确认库表存在；② mvn tomcat7:run 启动后端；③ 浏览器访问 localhost:8080；④ 预先同步一次确保有数据；⑤ 准备 DeepSeek CSV 样例备用。"]
];

const children = [
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 400 }, children: [new TextRun({ text: 'AI Usage Monitor', bold: true, size: 40 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: '毕业设计 / 课程答辩文稿', size: 28 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 }, children: [new TextRun({ text: '（含演讲稿 + 40 组问答）', size: 24, italics: true })] }),

  h1('一、开场白（约 30 秒）'),
  p('各位老师好，我是【姓名】，今天汇报的题目是《AI Usage Monitor——多平台 AI 用量统一监控与可视化系统》。'),
  p('随着 ChatGPT、Claude、DeepSeek、Codex、Grok 等 AI 工具在日常开发中广泛使用，Token 消耗和 API 费用分散在各个平台，缺乏统一视图。本项目旨在解决「看不清、算不清、管不住」的问题，实现多服务用量聚合、趋势分析和成本监测。'),
  p('下面我将从项目背景、系统架构、核心功能、技术实现和总结展望五个方面进行汇报，请各位老师批评指正。'),

  h1('二、项目背景与目标（约 1 分钟）'),
  p('本项目源于个人在多 AI 平台并行使用时的痛点：OpenAI 有 Organization Usage API，DeepSeek 只有余额接口，Codex 和 Grok 的数据在本地日志里，格式各不相同。'),
  p('项目最初实现为 Python + PyQt6 桌面版，支持托盘常驻和本地 SQLite 存储。为便于答辩演示和 Web 访问，我将其迁移为 H5 版本：前端 Vue 3 + ECharts，后端 Java Servlet + MySQL，Tomcat 部署。'),
  p('项目目标可以概括为三点：统一采集、可视分析、可扩展架构。'),

  h1('三、系统架构讲解（约 2 分钟）'),
  p('系统采用 B/S 三层架构。表现层是 Vue 3 单页应用，包含 Dashboard 仪表盘、ServiceCard 服务卡片、UsageChart 趋势图和 SettingsModal 设置弹窗。'),
  p('业务层由 Java Servlet 提供 REST 风格 API，核心模块包括：AuthServlet 负责 API Key 校验与存储；UsageServlet 负责汇总、历史和同步；SyncScheduler 后台定时调度各 Collector 采集器。'),
  p('数据层使用 MySQL，核心表 usage_snapshots 按「服务 + 模型 + 日期」存储每日 Token、请求数、费用等快照；api_keys 表按服务隔离存储密钥；auth_status 记录校验状态。'),
  p('采集器采用策略模式：每个 AI 服务实现 BaseCollector 接口，统一输出 UsageRecord，由调度器批量执行后 upsert 入库。远程 API 型（如 ChatGPT）和本地日志型（如 Codex、Grok、Claude Code）分开调度。'),

  h1('四、核心功能演示讲解（约 3 分钟）'),
  p('【演示 1】打开 Dashboard，可以看到 8 个服务卡片：ChatGPT、Claude、KIMI、GLM、DeepSeek、Claude Code、Grok Build、Codex。每个卡片展示本月费用或余额、请求次数、Token 消耗和同步状态。'),
  p('【演示 2】进入设置页，输入 API Key 并点击「校验并保存」。后端会向官方 API 发起轻量验证，通过后写入数据库并触发同步。'),
  p('【演示 3】点击「同步用量」，调度器执行全部 Collector。ChatGPT 从 OpenAI Usage API 拉取 30 天数据；Codex 扫描 ~/.codex/sessions 下的 rollout JSONL，解析 token_count 事件；Grok 读取 ~/.grok/sessions 下的 summary.json。'),
  p('【演示 4】选中某个服务，下方历史趋势图支持切换视图。Token 分析使用按日聚合的折线图；DeepSeek 额外支持账户余额、消费趋势，并可通过平台导出的 CSV/ZIP 导入真实 Token 数据。'),

  h1('五、技术亮点（约 1.5 分钟）'),
  p('第一，迁移可复用：Collector 抽象从 Python 延续到 Java，降低跨技术栈迁移成本。'),
  p('第二，多源融合：同一套数据模型兼容远程 API、本地 JSONL、CSV 导入三种来源。'),
  p('第三，工程化部署：Vite 构建产物直接输出到 webapp，单 Tomcat 即可运行前后端。'),
  p('第四，面向真实限制设计：DeepSeek 无历史 Token API，采用余额差值 + CSV 双轨方案，而非简单报错。'),

  h1('六、总结与展望（约 30 秒）'),
  p('本项目完成了从桌面到 Web 的迁移，实现了 8 类 AI 服务的用量监控与趋势可视化，验证了 Collector + Scheduler + 关系型存储的可扩展架构。'),
  p('后续计划包括：API Key 加密存储、多用户登录、超额告警通知，以及接入更多平台的官方账单接口。'),
  p('以上是我的汇报，感谢各位老师聆听，请批评指正。'),

  new Paragraph({ children: [new PageBreak()] }),
  h1('七、答辩问答预案（40 组）'),
  p('以下问答覆盖：迁移方案、多凭证设计、技术选型、架构设计、数据采集、安全与局限、测试演示等老师常问方向。建议熟读「加粗问题」，回答时先结论后展开。'),
  ...qaList.flatMap(([q, a]) => qa(q, a))
];

const doc = new Document({ sections: [{ properties: {}, children }] });
Packer.toBuffer(doc).then(buffer => {
  const out = '/Users/daijinglin/web/AI-Usage-Monitor/答辩文稿-AI-Usage-Monitor.docx';
  fs.writeFileSync(out, buffer);
  console.log('Generated:', out);
});