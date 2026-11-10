# 海舵实施记录

当前最新：WooCommerce本地真实联调已完成，46项后端/4项单元/13项原浏览器+1项Woo浏览器、自动Webhook和398元履约闭环通过。见[联调报告](WOOCOMMERCE_INTEGRATION.md)，新增远程CI待本轮推送；下方尚未接通为历史。

最新增量：本地WooCommerce测试店已实际自建并验证后台登录/商品API；尚未接通海舵。见[运行说明](WOOCOMMERCE_LOCAL.md)及文末记录。

当前最新结论（2026-11-09）：已通过GitHub API核实提交511f1a4的四项远程CI及Linux恢复演练全部通过，证据[remote-ci-acceptance.json](remote-ci-acceptance.json)。剩余4组真实外部验收缺配置/权限，详情见文末；下方尚缺远程仓库等为历史。

## 最新验收顺序与阻塞（2026-11-06）

1. 远程CI：缺目标仓库/连接，未执行；已补只读仓库权限、作业超时及恢复演练步骤，YAML本地解析通过，不标远程通过。
2. 备份恢复：本地完整通过。33张表行数、19文件哈希、库存约束，恢复API登录/企业选择/订单读取/授权图片哈希均通过；最终演练20.10秒，不等于生产RTO。证据backup-restore-verification.json、backup-restore-drill.log，详见[备份恢复](BACKUP_RESTORE.md)。
3. Shopify/S3/模型真实验收：已核对.env中必要配置仍缺，未发起外部调用；缺项清单external-acceptance-dependencies.json，不含密钥值。部署目标/授权仍缺。待用户提供测试条件，未伪造通过。

本轮没有修改业务后端或前端代码，未重跑41/4/13；使用既有已验收镜像验证恢复。原库和数据卷保留，核心五服务恢复健康。下文2026-11-05记录为历史。

最新验收：企业向量索引维护已完成；完整后端41项零失败/错误/跳过、浏览器13项通过55.8秒、单元4项及构建通过。默认配置下索引禁用/重建503与核心业务可用均复核。详细记录见文末及[维护说明](KNOWLEDGE_INDEX_MAINTENANCE.md)。下方40/11或5项是历史结果。

最新增量：本地告警规则、5组规则测试及真实加载验证完成。完成差距见[剩余事项](REMAINING_WORK.md)，细节和证据见文末本轮记录。

## 本轮完善（2026-11-05）

页面按需加载与失败恢复已编写并验证：入口gzip体积减少约49%，本轮5项浏览器及4项单元测试通过。详细证据和剩余限制见文末增量记录；不把历史40项后端/11项浏览器结果写成本轮重跑。

## 当前交付状态（2026-11-05，首版本地验收完成）

原始首版范围内可在本地完成的开发与验收已完成。Boot4.1.2 / Modulith2.1.1 / Spring AI2.0.2及Java21：后端真实集成40项零失败/零错误/零跳过，浏览器11项通过（50.7秒），Vitest4项及前端类型检查/构建通过。空库20条迁移和演示数据、重启幂等、消息故障恢复、Redis会话、文件持久化、监控与增强关闭后核心可用均实际验证。依据是final-*与boot4-*日志，不是本文勾选。

完整能力、证据、模拟与未完成事项见[首版交付报告](COMPLETION_REPORT.md)及[原始需求矩阵](FINAL_ACCEPTANCE_MATRIX.md)。真实Shopify/S3/模型、远程CI和部署仍需外部凭据/权限；不声明整个生产环境已验收。以下阶段记录保留为历史，不代表最新结果。

## 阶段三当前进度（2026-11-05）

阶段三本地实现与验收已完成；真实模型/Shopify/S3及GitHub远程CI未验证。完整范围与边界见[阶段三报告](PHASE_THREE_REPORT.md)，下文阶段二/阶段一为历史记录。

| 能力 | 代码状态 | 实际证据 |
|---|---|---|
| PWA静态缓存/离线提示 | 已实现 | 浏览器通过，不缓存API；OS安装交互未验证 |
| OpenSearch | 已实现，手动索引 | 真实查询、跨企业隔离、停容器降级均通过 |
| AI/知识库/草稿 | 已实现 | 固定演示浏览器通过，真实模型仅模拟HTTP契约通过 |
| pgvector | 已实现，独立库 | 真实pgvector租户隔离及完整浏览器使用通过 |
| 模型额度/超时 | 已实现 | 真实PG100次额度边界、阻塞模型15秒超时通过 |
| Prometheus/Grafana/OTel | 已实现 | 抓取up=1、面板自动配置、Collector实际收到spans |
| 后端JUnit/容器回归 | 已验证 | phase-three-regression.log：33通过，0失败/错误/跳过 |
| Vitest | 已验证 | 3通过；升级4.1.12后官方审计0已知漏洞 |
| 浏览器整体 | 已验证 | 最终8通过，35.0秒；phase-three-browser-regression.log |
| 构建/锁文件/CI | 本地通过，CI配置已编写 | 远程CI无仓库权限，未执行；不等于通过 |
| k6 | 已执行两种基线 | 会话475请求0失败；认证订单60条、481请求0失败；详见性能报告 |

本轮自行修复Docker未启动、UTF-8 BOM、PWA离线回退、浏览器重复标签与重复知识排名、AI超时线程等待、配额独立提交，以及Vitest已知漏洞。没有修改真实平台数据或部署。

## 阶段二当前进度（2026-11-05）

阶段二本地实现及可复现验收已完成；真实Shopify/S3联调待凭据，不标记为通过。阶段三未启动。阶段一历史结论见下文，当前阶段二完整边界见[阶段二报告](PHASE_TWO_REPORT.md)。

| 工作项 | 代码状态 | 实际验证 |
|---|---|---|
| Redis会话 | 已实现 | API容器替换后原会话仍可访问 |
| Outbox/RabbitMQ/同步任务 | 已实现 | 真实PG/RabbitMQ事务、确认、重复、死信及恢复测试 |
| 管理员人工重放 | 已实现 | 故障回滚后恢复；运营403、跨企业404、已完成409 |
| 物理重启恢复 | 已实现 | Rabbit停止后重试落库，实际替换API容器，再恢复仅1次处理 |
| Shopify授权/刷新/GraphQL/Webhook | 已实现 | 固定HTTP模拟及真实PG测试通过；真实平台待凭据 |
| MOCK连接器/同步页面 | 已实现 | 后台与浏览器通过，明确MOCK固定演示 |
| 本地图片/S3适配器 | 已实现 | 图片上传、读取、重启持久化；S3仅HTTP契约通过 |
| 完整五服务Compose/Nginx | 已实现 | 构建、健康启动、动态DNS恢复通过 |
| 后端回归 | 已验证 | phase-two-regression.log：29通过、0失败/错误/跳过 |
| 前端与镜像构建 | 已验证 | frontend-build.log、compose-build.log、compose-web-build.log |
| 浏览器最终回归 | 已验证 | phase-two-browser.log：6通过，29.8秒 |

关键修复：店铺创建事务审计/Outbox、汇率校验、非法存储后端配置、API换IP后Nginx502、浏览器异步选择器和PowerShell故障脚本。实际恢复脚本：infra/verify-stage-two.ps1；证据：docs/stage-two-recovery.log。

## 阶段一历史进度

阶段一可演示业务闭环：已验收交付。全项目要求：尚未完成。该结论为阶段一历史记录；阶段二进度见顶部。

完整结论、首版边界与未完成项见[阶段一报告](PHASE_ONE_REPORT.md)，不得将闭环验收解释为生产上线或全项目验收通过。

| 工作项 | 当前状态 | 实测证据 |
|---|---|---|
| 登录/CSRF/租户/角色 | 已验证核心路径 | verify-auth.ps1及浏览器 |
| 成员创建/角色管理 | 已验证 | 创建财务员工登录；最后管理员409、跨企业404 |
| Product/SKU/店铺/映射 | 已验证核心路径 | core/listing浏览器；JPA企业限定读取HTTP |
| CSV文件/文本/幂等 | 已验证 | 含BOM文件导入，连续10次只1订单/明细 |
| 多仓入库/预占/取消/发货 | 已验证核心路径 | PostgreSQL并发/回滚/取消/跨仓及浏览器 |
| 入库故障恢复 | 已验证 | 后端已入库、浏览器丢响应，刷新重试duplicate=true且库存不翻倍 |
| 费用/利润 | 已验证预估口径 | 原币快照、398.00样例，未宣称实际结算 |
| 总览/趋势/异常订单 | 已实现并有实测 | 后端汇总/HTTP异常；浏览器登录总览 |
| 服务器分页 | 已验证 | 商品/订单/库存总数及跨页不同记录 |
| 移动端 | 已验证核心路径 | 390x844登录、抽屉导航 |
| 演示初始化 | 已验证最终空库与重启 | 10迁移、2企业/8成员/2SKU/2订单，库存97不被重置 |
| Redis会话恢复 | 已验证 | API重启后原Cookie及选中企业恢复 |
| 工程构建/测试 | 已通过 | Maven/Vite、Testcontainers9项、浏览器5项 |

## 最终验证记录

- 后端打包BUILD SUCCESS：docs/backend-build.log。
- 前端类型检查与生产构建通过：docs/frontend-build.log；仍有大chunk提示。
- 独立PostgreSQL容器：9通过、0失败/错误/跳过，其中6库存、1利润、2请求关联标识，docs/testcontainers.log。
- 浏览器最终回归：5 passed (43.9s)，docs/browser-tests.log。使用Playwright1.56.1及显式本机Chromium1161路径。
- HTTP：CSRF/角色/退出、重复10次导入、企业B列表/详情/费用写入拒绝、JPA目录跨企业404通过。
- 在线npm审计：0已知漏洞，docs/npm-audit.json；不代表不存在未知漏洞。
- 上述结果仅对应所列测试。没有执行k6，没有性能报告，不虚构延迟、吞吐量或错误率。

## 本轮完成内容

V9/V10费用快照及独立预占模型；JPA商品目录/Modulith catalog边界；Testcontainers生命周期；员工创建；服务器分页；ECharts真实数据趋势；异常订单；浏览器时区；离线提示及写入保护；用户/企业限定查询缓存；API no-store；随机请求关联标识；刷新后的入库幂等凭据恢复；接口/架构/中文运行文档。

## 尚未完成及归属

- 全项目功能：复杂拆单、多规格、多行CSV、真实承运商/轨迹、实际结算锁定、独立ExchangeRateSnapshot实体、完整模块拆分/英文资源等未完成。详细边界见报告，不作通过标记。
- 阶段二：RabbitMQ/Transactional Outbox、SyncJob、Webhook/Shopify、文件存储；真实外部连接需要凭据。
- 阶段三：搜索/AI/向量、PWA、监控、k6、完整CI。Playwright已提前用于阶段一验证，不表示阶段三完成。
- 无需用户调整环境。本轮没有部署、没有修改真实平台数据。

## 下一步

优先取得真实Shopify/S3测试凭据并执行外部验收；此前不声称真实集成完成。下一开发阶段为阶段三搜索/AI/PWA/监控/CI与实际压测，本轮未开始。

## 运行与证据入口

[README](../README.md) · [接口说明](API.md) · [核心OpenAPI](openapi.yaml) · [架构](ARCHITECTURE.md) · [验收门槛](PHASE_ONE_ACCEPTANCE.md) · [历史过程](IMPLEMENTATION_HISTORY.md)

## 文末最新摘要（2026-11-05，阶段二）

阶段二本地实现及验收完成：29项后端回归、6项浏览器回归（29.8秒）、前后端镜像构建、五服务健康启动、实际停Rabbit/替换API恢复、权限重放、Redis会话与图片持久化均通过。Shopify/S3真实联调受凭据限制，未标通过；MOCK/HTTP契约明确标注。新增V11-V15迁移、阶段二报告、接口及运行文档。没有执行压测或部署；阶段三未启动。无需用户修改本机环境，若需真实平台验收，下一步配置外部凭据。
验收清理：最终浏览器6项/29.8秒通过后，api/web验收容器已停止；PostgreSQL、Redis、RabbitMQ及全部数据/图片卷保留。再次演示执行README的Compose up命令。compose-status.log为清理前五服务健康快照。

## 文末最新摘要（阶段三进行中）
PWA代码及前端构建通过；离线浏览器1项通过。Docker引擎已自行恢复，五服务健康。OpenSearch3.3.2仅验证容器可启动/版本响应，尚未接入商品查询；独立search profile配置已编写。Spring AI1.1.8依赖可解析，不等于AI功能完成。k6会话基线实际475请求、0失败、p95 19.12ms，详见PHASE_THREE_PERFORMANCE.md；不代表业务性能。AI/知识库/pgvector、搜索集成、监控、Vitest/CI等仍待完成。

## 最新增量（第三阶段，2026-11-05）
已编写并打包：企业独立OpenSearch索引/数据库二次授权/数据库降级；知识库文本切分、数组向量、租户限定检索与引用；固定演示文案待审核；每日100次额度及输入限制；独立9090 Actuator/Prometheus。实际验证：KnowledgeContainerTest真实PG1通过；AI/搜索浏览器1通过（5.5秒）；Vitest3通过。尚未完成：pgvector、真实模型配置、监控完整链路、CI、搜索隔离/降级和最终回归。不能把依赖已加入视为功能完成。

## 文末最终摘要（2026-11-05，第三阶段）

第三阶段本地实现与验收完成：后端33项0失败/错误/跳过，Vitest3项通过，浏览器8项35.0秒通过，最终镜像构建通过，官方npm审计0已知漏洞。搜索企业隔离/故障降级、知识来源/pgvector租户隔离、草稿、模型配置模拟HTTP、额度边界/15秒超时、Prometheus抓取/Grafana自动面板/OTLP实际接收均有实际证据。关闭增强配置后核心订单及数据库搜索可用，AI明确禁用。k6会话与认证订单基线均已实际执行，不外推业务容量。

真实模型/Shopify/S3联调仍缺外部凭据，GitHub远程CI缺仓库连接与执行权限；OS安装交互、Linux/macOS宿主未验证。下一优先级是提供外部测试条件后验收，详细边界见PHASE_THREE_REPORT.md。没有部署或修改真实平台数据。

验收清理：api/web、OpenSearch/临时向量库、Prometheus/Grafana/Collector均已停止；核心PostgreSQL、Redis、RabbitMQ及数据/文件卷保留。Grafana实际API读取面板2项通过，密码已保存于忽略的.env，不输出。无等待中的测试或构建。

## 本轮补齐进度
后端40项真实数据库/消息/向量与模块权限回归通过（completion-regression.log）。Modulith实际验证10个具名业务/适配模块无循环/未声明依赖。新增角色能力模型、同商品多个SKU、承运商事件适配接口、独立汇率与人工实际版本、订单CSV导出、中英文基础资源。新增浏览器物流/实际贡献/导出隔离、多SKU多商品订单已通过；PWA普通浏览器安装条件与离线2项通过。增强全量回归进行中，不能先标最终完成。

## 最新进度：受支持版本迁移（进行中）

本轮新增功能的Boot3.5基线完整40项后端、11项浏览器（53.9秒）已通过；普通浏览器PWA安装条件通过。官方确认Boot3.5已结束OSS维护，因此不把该基线标为满足原始版本要求。已在target备份基线，正在迁移Boot4.1.2 / Modulith2.1.1 / SpringAI2.0.2；当前仅依赖下载，尚未标编译或兼容通过（boot4-migration-build.log）。

版本迁移增量：Boot4.1.2/Modulith2.1.1/AI2.0.2关键6项与完整40项测试已通过（boot4-regression.log），后端镜像构建通过。按官方迁移指南补Flyway/RedisSession/MVC/OTel starter；正在验证实际应用启动。新增会话namespace=haiduo:session:v4，大版本升级须重新登录，不删除旧业务数据。

环境恢复记录：Docker Engine曾返回500，安全重启45秒超时。仅重启Docker专用WSL及Docker Desktop后台后恢复，docker info实测29.9.2；没有删除容器或数据卷。一次只读状态检查自动审批超时，按工具提示重试后成功。正在继续剩余运行验收。

## 文末最新状态：最终回归完成（2026-11-05）

- 代码已编写：V17-V20、模拟轨迹、多SKU/多行CSV、独立汇率快照、不可变人工实际贡献、导出、角色权限、10模块边界、英文基础资源、自动商品索引，以及受支持框架迁移。
- 已实际验证：final-backend-integration.log为40测试零失败/错误/跳过；final-browser-regression.log为11通过50.7秒；final-vitest.log为4通过；final-web-build.log含类型检查与构建成功。
- 运行验证：boot4-auth-check.log、boot4-isolation-check.log、boot4-clean-bootstrap-final.log、boot4-recovery-check.log、boot4-fallback.log；最终Prometheus up=1、Collector收到trace、Grafana2面板。OpenAPI实际解析30路径，全部profile Compose配置校验通过。
- 修复过程：Docker Desktop恢复后重新无缓存构建，Dockerfile校验JAR完整/非空，实际启动后闭环通过。初次普通Maven测试32项跳过不计通过，已显式开启集成测试重跑；新增verify-backend.ps1防止遗漏开关。Playwright默认二进制缺失，通过指定本机Chrome完成验收。
- 尚未验证：真实外部Shopify/S3/模型、GitHub远程Actions、生产部署和TLS、Linux/macOS宿主与OS安装按钮交互。复杂拆单等首版边界见交付报告；历史k6仅升级前实际只读基线，未伪报新版性能。
- 下一优先任务：取得独立外部测试账号/凭据、HTTPS回调和明确费用/部署授权后真实联调。当前无需修改Java版本；不自动开通账号或发布。

当前结论与完整限制：[COMPLETION_REPORT.md](COMPLETION_REPORT.md)。本节覆盖上方历史“升级进行中”“待回归”等状态。
最终复核：verify-backend.ps1自身实际运行40项通过（final-backend-script.log）。核心五服务保持健康，可访问http://localhost:8088；增强测试服务停止、数据保留（final-container-status.log）。默认AI禁用/数据库搜索/订单可用再验证通过（final-core-check.log）。所有测试与构建已结束。

## 增量完善：页面按需加载（2026-11-05）

代码已编写：业务页使用React.lazy/Suspense，登录不提前下载业务页和图表；新增PageBoundary，加载失败显示中文说明和重新加载按钮。未引入新依赖。

实际验证：improvement-web-build.log类型检查/构建通过，入口JS从1658.39kB降至856.54kB（gzip537.82至276.03kB，约减少49%）；不是实测加载耗时。improvement-vitest.log为4通过；improvement-browser.log为5通过17.0秒，覆盖订单/398利润、手机导航、PWA安装条件/离线及新增的资源加载失败恢复。前端镜像构建/启动见improvement-web-image.log和improvement-web-start.log。

首次4通过1失败：多订单数据使旧core测试全页面UNKNOWN定位不唯一；已限定订单详情抽屉，未清空数据，复跑5项全通过。首次日志保留improvement-browser-first-run.log。后端未改，本轮未重复后端40项或增强11项全量；新增用例已纳入CI的全目录Playwright测试。

入口与图表仍有超过500kB提示，未通过调大阈值掩盖。外部凭据和远程验收依赖维持原结论。核心五服务保留，访问http://localhost:8088。

官方依据：[React lazy](https://react.dev/reference/react/lazy)、[Vite动态导入](https://vite.dev/guide/features.html#async-chunk-loading-optimization)。
## 本轮完善：告警规则与完成差距（2026-11-05）

代码已编写：Prometheus新增API抓取失败/目标缺失持续5分钟、HTTP 5xx>5%且流量>=1/s持续10分钟两条规则；添加5组promtool测试、CI监控检查作业及中文排查手册。未配置通知发送，不冒充生产告警体系。

实际验证：alerts-rule-tests.log为SUCCESS；alerts-config-check.log为配置/规则成功；alerts-runtime-check.json实测两规则health=ok/state=inactive、API up=1、HTTP指标25条序列；Compose监控配置校验通过。只启动Prometheus，不重启API、不停库，不造真实业务错误。没有重跑业务后端/前端测试，也没有执行远程Actions。Prometheus保持运行供http://localhost:9091/alerts查看，核心五服务保持原状。

距完成的判断：本地学习/作品首版已验收；若要求真实集成并发布，仍有Shopify、S3、真实模型、远程CI、部署/跨环境验收5组依赖，不是5个等量任务，不给虚构百分比或工时。详见REMAINING_WORK.md；告警阈值为演示决策，生产需校准及通知渠道。

## 本轮完成：企业向量索引维护（2026-11-05）

代码已编写：管理员可用主库已有向量完整重建当前企业独立索引并清理孤立记录；不调用模型、不改变知识正文、不替换模型嵌入。主库按企业锁协调导入和重建；向量库删除/写入同一事务。新增状态/重建REST、CSRF/角色校验、审计、中文维护卡片和32路径OpenAPI。

实际验证：vector-maintenance-backend.log为41测试零失败/错误/跳过，真实pgvector测试重建10次、孤立清理、跨企业隔离、CHECK故障中途回滚及空来源清理；vector-maintenance-browser.log为全量13通过55.8秒（新增维护/权限及前次页面加载恢复均包括）；Vitest4、类型检查、前后端镜像构建和启动通过。OpenAPI解析32路径通过。早期用重复ID模拟约束失败的测试无效，因为upsert正常更新，已改真实CHECK约束并复验；保留失败日志，不算通过。

收尾：恢复核心默认配置，AI明确禁用/主库搜索/订单可用，索引状态disabled及POST重建503均实际验证；日志vector-maintenance-core-check.log、vector-maintenance-disabled-check.log。临时搜索/向量服务停止，数据保留，核心五服务及原Prometheus继续运行。没有长时间等待退出的构建或测试。

边界：手工重建不等于跨模型重新向量化，也未实现定时清理；外部Shopify/S3/真实模型、远程CI及部署5组依赖不变。下一步仍可按REMAINING_WORK.md准备真实验收条件。完整设计与复现见KNOWLEDGE_INDEX_MAINTENANCE.md。

## 顺序验收收尾（2026-11-06）

代码已编写：verify-backup-restore.ps1及中文备份手册；数据库custom dump与对象归档/哈希；隔离恢复、恢复API授权登录/企业/订单/图片下载；备份目录加入Git/Docker忽略；CI只读权限、超时与pwsh恢复演练步骤。脚本覆盖Windows PowerShell调用，Linux/macOS宿主兼容未实测。

实际通过：backup-restore-verification.json最终PASS，33表行数、19文件哈希、归档哈希、库存约束、恢复应用及授权文件API一致，耗时20.10秒。backup-safety-check.log验证拒绝覆盖/路径穿越；backup-ci-yaml-check.log仅YAML解析，不冒充远程Actions；原API认证/CSRF/跨租户角色及核心降级检查通过，恢复测试容器停止，原数据保留。

过程：先做纯数据恢复，再补应用登录；最终显式指定恢复API的FILE_ROOT=/data/files和local后端，并添加授权图片哈希验证，避免只挂载文件卷却未验证应用使用它。最终结果以acceptance-final-20261106为准；以前的中间演练保留，不计独立功能数量。

未完成且当前无法独立处理：目标GitHub仓库/连接未提供；.env缺Shopify客户端/回调/加密主密钥、S3桶/访问凭据、AI凭据及模型名称，部署目标/证书/授权未提供。external-acceptance-dependencies.json只记录缺项名，不输出凭据。已向用户询问GitHub仓库URL；不自动开通、购买或发布。下一步接入目标仓库并验证远程Actions，之后按剩余清单真实联调。

本轮未修改业务代码，未重跑41项后端/4项单元/13项浏览器全量，不把昨日结果写成今日执行。备份不含密钥、Redis/RabbitMQ完整容灾、异地加密或生产恢复策略，边界见BACKUP_RESTORE.md。
## GitHub Actions Node24迁移（2026-11-07）

收到browser失败报告，但提供内容仅为Node20弃用通知，不含失败堆栈，尚不能确认根因。已将checkout4.2.2/setup-node4.4.0/setup-java4.7.1最小升级到固定5.0.0；三者官方action.yml均声明node24。应用Node24.22.1与Java21不变，未启用不安全Node20开关。工作流YAML本地解析通过，未执行远程CI、不声称browser已修复。需用户提供失败运行链接/首个Error日志并推送修改后重新验收。已发现本地origin存在，之前缺仓库的记录现为历史；无全局safe.directory修改，无提交或推送。
## CI无图片商品上传测试超时修复（2026-11-08）

根据用户提供的远程日志，stage-two用例失败，另12项通过。根因是上传前对可不存在的table img调用getAttribute，首次空图片环境等待耗尽30秒总预算；页面关闭为超时清理后果，与Node20通知不是同一问题。改为定位DEMO-BAG商品行，先判断img数量再读旧src，上传/新图断言均限定该行，未延长超时。

回归将第一次筛选结果的图片展示置空以覆盖本地已有图片的情形；只有初始展示字段被模拟，上传、授权下载、图片内容校验、跨企业404和消息同步仍用真实后端。ci-image-test-regression.log：1 passed (11.3s)；测试加载检查通过。未重跑全部13项或远程Actions；无业务代码改动。核心服务保持运行，下一步提交修复后验收新的远程运行。
## Linux CI Docker命令发现修复（2026-11-08）

用户提供恢复演练首步失败日志：Get-Command在Linux返回/usr/bin/docker和/bin/docker，Source数组被传给调用运算符后作为一个命令名，导致exit1。两条发现分支现均限定Application并Select-Object -First 1，将最终路径约束为字符串且检查非空。没有更改备份或恢复数据逻辑。

实际验证：直接提取脚本中的发现代码，模拟docker.exe不存在、docker返回两个路径，成功仅选择并调用第一个可执行文件；脚本语法检查通过；本机真实Docker --version调用通过。未在真实Linux runner重跑整套演练，不标远程CI通过。下一步提交修改后触发新工作流，不能重跑旧提交代替新代码验收。
## 顺序验收：远程CI正式核实（2026-11-09）

通过GitHub公开API实测核实运行42472427358，提交511f1a4950f767ab0c5f5e0197d92efaa228a470：browser/backend/frontend/monitoring四作业均success，Backup and isolated restore drill步骤completed/success。证据remote-ci-acceptance.json。先前缺仓库、远程未运行和Linux恢复未验收的描述为历史；当前该提交的Ubuntu CI/恢复演练已实际通过，不等同macOS宿主或生产部署通过。本轮只读核验，没有触发付费任务或发布。

后续真实验收已检查条件：Shopify缺CLIENT_ID/SECRET/REDIRECT_URI/TOKEN_ENCRYPTION_KEY，S3缺桶与访问凭据，AI缺BASE_URL/API_KEY/聊天和嵌入模型名；部署目标/域名/TLS/授权未提供。不能继续真实平台验收，未调用外部业务或付费模型。现在剩4组外部验收：Shopify、S3、真实模型、部署与环境验收，CI已关闭缺口。

本地Docker当前未启动；本轮未新增业务代码、不需要重跑本机测试。41/4/13为历史本地基线，远程通过以指定提交与作业证据为准，不虚报本轮本地执行。可选增强（定时清理、跨模型重新嵌入、完整英文UI等）仍非首版必需；本轮优先完成真实验收收尾，不将新可选功能冒充已实现。
## 本地WooCommerce自建（2026-11-09）

代码已编写：独立compose.woocommerce.yml、start-woocommerce.ps1、PHP幂等初始化、凭据模板及中文手册；本地8090、数据库不发布端口、独立卷；密码文件加入Git/Docker忽略，不改海舵.env和数据库。固定WP7.1.4/Woo11.2.2/PHP8.3/MariaDB11.4.9。

实际验证：两容器启动，DEMO-BAG及种子待付款订单初始化，重复启动通过；真实Chrome后台登录及商品Store API通过，证据woocommerce-check.json、woocommerce-verification.log。PHP非Web初始化与首次安装订单存储切换问题已修正，后台最终复验成功；未删库重建。仅配置本机测试，无费用/真实收款/真实发货。

尚未实现：海舵WooCommerce连接器、管理员订单REST认证、Webhook及订单库存联调。下一步扩展平台连接器并验证两系统交互；不能把测试店启动当作海舵接通，也不能把Woo成功当作Shopify验收。41/4/13为海舵历史基线，本轮不重跑未改动业务测试。详见WOOCOMMERCE_LOCAL.md。
## WooCommerce本地真实连接验收（2026-11-09）

已实现：V21连接表/平台枚举、V22Webhook密钥；只读OAuth1订单适配器（传输与签名地址分离）、可信地址白名单、企业管理员配置/AES-GCM密钥、SKU映射/当前企业回退、BigDecimal收入/商品退款、分页上限/超时重试、原始签名Webhook/业务载荷去重/Outbox权威重取及本地cron；同秒权威变化仍处理，重复状态无重复库存副作用。店铺筛选、仓库搜索和配置反馈已补齐。

实际验证：46项后端零失败/错误/跳过（woo-adapter-backend.log），单元4通过，原浏览器13通过58.1秒（woo-core-browser.log），真实Woo浏览器1通过5.1秒，十次同步一单/企业与角色拒绝；Woo实际十次回调一条处理事件/伪造签名401/库存无副作用；普通更新经cron自动投递通过；模拟付款状态、预占、发货10仅一运单、费用50/72、398元、确认10均通过。独立空库首次及重复建店、Chrome后台登录通过，临时项目/两个卷已清理。默认核心降级通过，当前运行核心5+Woo店铺/DB/cron3服务，数据保留。

新增CI woocommerce作业及35路径OpenAPI、启动/验收脚本、固定摘要镜像。新增作业尚未远程执行；此前511f1a4四作业成功是历史基线，不自动覆盖新代码。详见WOOCOMMERCE_INTEGRATION.md。没有真实付款/邮件/承运商发货或Woo写状态回传；测试订单付款状态为明确夹具。

过程修复：空商品初始化失败记录经条件核实移入回收站，未永久删订单或放松SKU校验；测试快照出现过本地凭据，已撤销旧ConsumerKey并轮换只读密钥，现表单提交即清空/连接toString及网络错误脱敏；Docker访问日志不含查询串，私有文件和测试结果均忽略。现有业务库与店铺数据未清空。
### 本轮最终核对（2026-11-09）
重新读取实际日志确认：后端46项零失败/错误/跳过，Woo浏览器1项5.1秒通过；自动cron回调与十次重复回调、履约398CNY验证均通过。YAML三份解析通过，git diff --check退出0（仅LF/CRLF提示）。当前Docker核心5个服务及Woo3个服务运行，所有已配置健康检查均healthy；未重复启动服务、未等待常驻进程退出、未清空业务数据。

当前停止点：本地免费平台演示闭环已完成；真实S3、真实AI、Shopify及正式部署需要外部账号/凭据/目标和授权，不能独立完成。新增Woo GitHub作业尚未远程运行，需提交并推送这批代码后验收，不能引用旧提交成功结果替代。本轮未执行Git提交或推送。下一优先事项：先对本批改动进行远程CI验收，再按需提供真实模型/S3配置；学习展示可直接使用本地Woo及明确标注的AI演示模式。

## Woo CI空订单失败排查（2026-11-10）
用户远程日志：Woo连接与配置成功、十次同步HTTP成功，但30秒内订单列表为0，当前根因未确认，不能标记修复。新增失败时/api/sync事件元数据诊断及CI always阶段400行API日志、Outbox/SyncJob/订单状态SQL；不打印连接密钥或客户地址，未放宽原业务断言或延长超时。
实际验证：Playwright --list成功发现1项，git diff --check退出0。未重新执行端到端业务测试。公开GitHub API返回rate limit exceeded，无法获取该次后台异常；本机Docker原未运行，已有限等待启动成功（29.9.2），当前无业务容器运行，未删除或修改卷。下一步需要失败运行的API Caused by异常，或提交诊断补丁重跑新提交后读取Diagnose local platform event processing步骤；此前本地通过不替代这次CI失败验收。

## Woo CI空订单根因修复（2026-11-10）
用户新日志：10个STORE_SYNC均SENT/COMPLETED，不是队列未处理。独立空库haiduo-woo-ci-debug复现：HPOS种子订单实际status=pending，wc_get_orders返回[]；初次激活插件在WordPress init之后，未注册wc-*状态。setup.php现显式注册WC_Post_Types::register_post_status()再创建种子，并对旧脚本生成的自有pending测试订单进行受限修复；添加标准订单查询可见性及商品行检查，避免再次假报初始化成功。不修改平台适配器、不增加超时。
实际验证：旧隔离测试记录修复后HPOS状态wc-pending、标准查询1单；另一全新项目haiduo-woo-ci-fixed首次初始化通过，真实Woo REST控制器GET /wc/v3/orders返回1个pending订单、DEMO-BAG、100.00；重复初始化后相同检查通过。git diff --check通过。未重跑海舵完整浏览器及远程CI；仍需推送setup.php及记录后验证新提交。两个临时项目与其自有四卷经名单核对后清理，原店铺数据保留。
