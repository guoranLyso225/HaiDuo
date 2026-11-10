# 海舵首版本地交付报告

最新增量：WooCommerce本地真实连接/回调/队列/履约利润已验收；本轮46后端、4单元、13原浏览器及1Woo浏览器通过，新增远程CI待推送。详见[Woo联调报告](WOOCOMMERCE_INTEGRATION.md)。旧的未接通/旧测试数量为历史。

2026-11-06增量：本地备份和隔离恢复完整通过，33表行数、19文件哈希、库存约束，以及恢复API登录/企业选择/订单/授权图片哈希均通过；最终20.10秒，不代表生产RTO。原库未覆盖。远程CI/真实外部验收仍阻塞，详见[备份报告](BACKUP_RESTORE.md)和实施记录文末。

最新增量验收：向量索引维护与孤立清理已完成；后端41项、单元4项、浏览器13项（55.8秒）通过，默认配置核心降级通过。详情见文末及[维护说明](KNOWLEDGE_INDEX_MAINTENANCE.md)。原表40/11项及未具备重建工具的描述属于历史状态。

2026-11-05，Windows、Java21、Docker Desktop Linux Engine29.9.2。本报告替代阶段报告中的“当前状态”；历史日志保留，不把阶段完成当作整体生产验收。原始首版范围内可在本地完成的功能和验收已完成，外部服务与部署仍有明确依赖。

## 已实现并实际验证

企业/成员/四角色与权限、Redis会话/CSRF、商品多SKU与图片、店铺映射、CSV多商品订单导入、多仓库存原子预占/释放/发货、流水、手动运单及模拟轨迹、预估贡献与不可变人工实际确认、独立汇率快照、看板、审计、订单CSV导出均已接通真实后端。原币金额以十进制字符串传输，BigDecimal/NUMERIC计算，历史快照不随现价静默修改。

RabbitMQ/Transactional Outbox、发布确认、消费幂等、退避/死信/权限重放、同步任务、Shopify适配器、授权本地文件与S3适配器已编写。搜索、知识库/pgvector、AI待审核草稿、PWA、监控与CI配置已编写并按下表分别验证。

| 实际检查 | 结果与证据 |
|---|---|
| Java21后端完整集成测试与打包 | `final-backend-integration.log`：40测试，失败0、错误0、跳过0，BUILD SUCCESS；真实PG、Rabbit、pgvector容器，不是内存库 |
| 浏览器前后端联调 | `final-browser-regression.log`：11通过，50.7秒，本机Chrome；包含租户隔离、多SKU多行CSV、预占发货、398贡献、物流/导出、图片/同步、手机导航、PWA |
| 前端单元/类型检查/构建 | `final-vitest.log`：4通过；`final-web-build.log`：tsc与Vite成功，存在超过500kB的包体积提示 |
| 登录与编码 | `boot4-auth-check.log`：CSRF、异企业、角色限制、退出和UTF-8通过 |
| 导入幂等/订单隔离 | `boot4-isolation-check.log`：十次导入一个订单/明细，异企业列表/详情/费用隔离通过 |
| 初始空库与重启 | `boot4-clean-bootstrap-final.log`：20迁移、2企业、8成员、2SKU/订单、6汇率快照；重启后库存97与快照数不变；测试独立库已停止，现有业务库未清空 |
| 消息恢复与持久化 | `boot4-recovery-check.log`：故障回滚、权限重放、实际停止Rabbit并替换API后恢复、Redis会话和图片存续；未配置Shopify明确503 |
| 关闭增强功能 | `boot4-fallback.log`：企业限定数据库搜索、AI明确禁用、核心订单可用 |
| 监控 | `final-prometheus-check.json`：api抓取up=1；`final-otel-check.log`：Collector实际收到spans；`final-grafana-check.json`：配置面板2个 |
| 容器 | `boot4-images.log`中JAR列表/非空检查和镜像导出完成；`boot4-enhanced-start.log`五服务及监控健康；全部profile Compose config校验成功 |
| API描述 | OpenAPI YAML实际解析30条路径；详见API.md、openapi.yaml。它是核心接口说明，不声称穷举每一个HTTP端点 |

真实PG并发验收属于上述InventoryContainerTest：100可用库存、200个不同订单各预占1，恰好100成功；多商品缺货整单回滚。TrackingSnapshotContainerTest验证十次重复事件/CSV/实际确认无重复副作用、乱序和终态保护、398.00以及历史不变。Modulith验证10个具名模块无非法依赖/循环。

性能数据继续保留于[实测报告](PHASE_THREE_PERFORMANCE.md)：这是升级前实际执行的只读基线，未在最终Boot4版本重跑，不能据此声称新版容量或生产SLO。库存并发正确性测试不等同吞吐压测。

## 支持版本迁移及恢复过程

Boot3.5官方已结束OSS支持，升级为Boot4.1.2、Modulith2.1.1、Spring AI2.0.2，Java21不变。补MVC/Flyway/Redis Session/OTel starter，迁移AI2模型配置、编码和OTLP属性。Shopify手工HTTP JSON仍用BOM管理的Jackson2，MVC/AI使用Jackson3。Redis会话命名空间改为`haiduo:session:v4`，旧会话需要重新登录，业务数据不删除。

官方依据：[Boot3.5维护终止说明](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)、[Boot项目版本](https://spring.io/projects/spring-boot/)、[Spring AI兼容要求](https://docs.spring.io/spring-ai/reference/getting-started.html)、[Boot4迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)。

Docker上次中断后先出现Engine500，恢复时未删除卷；后发现旧镜像`/app/app.jar`为0字节。Dockerfile新增`jar --list`与`test -s`，重新无缓存构建，实际启动/浏览器验收已通过。本轮续接时Docker未启动，启动Desktop后恢复。构建工具曾返回非零包装结果，不能只据退出包装断言成功；这里的镜像结论由构建日志、完整性检查和实际运行共同支持。

一次普通`mvn verify`因未开启环境开关，46测试中32跳过（`final-backend-verify.log`），不计入完整集成通过。随后按CI显式开启开关得到40项全部执行。新增`infra/verify-backend.ps1`统一设置开关并检查报告中没有失败/错误/跳过，脚本自身已实际复验40项通过（`final-backend-script.log`），并处理Windows PowerShell原生命令stderr警告问题。浏览器第一次缺少Playwright默认下载的二进制；使用已安装Chrome后11项通过，没有假定未下载的浏览器已安装。

## 模拟能力

- MOCK店铺/订单同步、物流事件为演示模拟，页面明确标注，不代表真实平台/承运商数据。
- AI_DEMO固定回答/草稿与字符哈希演示嵌入明确非模型调用；真实模型禁用时明确说明，不消耗付费模型额度。
- Shopify及S3的HTTP契约测试使用模拟服务，验证映射、签名、分页/限流/重试等适配行为，不冒充外部真实联调。
- “实际贡献”是有来源的人工确认不可变版本，并非支付平台自动结算凭证。

## 已编写但尚未完成外部真实验收

1. Shopify：需要测试店、应用Client ID/Secret、加密密钥、授权权限、已注册HTTPS回调/Webhook地址；真实读取/授权/回调待验证。
2. S3：需要独立测试桶、endpoint、region和最小权限凭据；真实上传下载/签名待验证。
3. 模型：需要兼容供应商地址、聊天/嵌入模型与凭据和调用费用授权；真实输出质量、延迟及费用待验证。不要将凭据提交到仓库。
4. GitHub CI：工作流已编写，本地等效检查通过；目录无Git仓库/远程连接，未执行push、Actions或发布。
5. 部署：需要目标环境、域名、TLS证书、生产密钥与明确授权；未部署、未开通付费服务、未修改真实店铺数据。

## 未完成与边界

首版明确不支持复杂拆单、真实承运商、Amazon连接、自动平台结算对账；完整英文界面、生产告警通知/长期trace存储/SLO/容量极限测试未实现（本地告警规则已在后续补齐，见文末）。英文基础翻译资源与连接器边界已预留。知识库向量为可重建派生数据，更换嵌入模型需重新导入，尚无孤立向量自动清理。

Linux/macOS宿主、多种真实手机、OS安装按钮交互未实际执行；普通Chrome的PWA安装条件与离线缓存隔离已通过。生产HTTPS Cookie配置已提供，未以本机HTTP测试替代公网TLS验证。前端包体积优化可后续进行，不影响本轮构建通过。

下一优先任务是提供上述外部测试条件后执行真实联调；在此之前可按README本地演示。详见[验收矩阵](FINAL_ACCEPTANCE_MATRIX.md)、[数据模型](DATA_MODEL.md)、[接口说明](API.md)。


最终服务状态：核心五服务保持运行且健康，访问 http://localhost:8088 。搜索/向量/监控测试服务与空库测试容器已停止，数据卷保留；默认模式AI明确禁用、搜索回退主库，最终复查见final-core-check.log、final-container-status.log。启用增强演示按README和阶段三运行文档操作。没有仍在等待退出的测试或构建。

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
## 顺序验收：远程CI正式核实（2026-11-09）

通过GitHub公开API实测核实运行42472427358，提交511f1a4950f767ab0c5f5e0197d92efaa228a470：browser/backend/frontend/monitoring四作业均success，Backup and isolated restore drill步骤completed/success。证据remote-ci-acceptance.json。先前缺仓库、远程未运行和Linux恢复未验收的描述为历史；当前该提交的Ubuntu CI/恢复演练已实际通过，不等同macOS宿主或生产部署通过。本轮只读核验，没有触发付费任务或发布。

后续真实验收已检查条件：Shopify缺CLIENT_ID/SECRET/REDIRECT_URI/TOKEN_ENCRYPTION_KEY，S3缺桶与访问凭据，AI缺BASE_URL/API_KEY/聊天和嵌入模型名；部署目标/域名/TLS/授权未提供。不能继续真实平台验收，未调用外部业务或付费模型。现在剩4组外部验收：Shopify、S3、真实模型、部署与环境验收，CI已关闭缺口。

本地Docker当前未启动；本轮未新增业务代码、不需要重跑本机测试。41/4/13为历史本地基线，远程通过以指定提交与作业证据为准，不虚报本轮本地执行。可选增强（定时清理、跨模型重新嵌入、完整英文UI等）仍非首版必需；本轮优先完成真实验收收尾，不将新可选功能冒充已实现。
## WooCommerce本地真实连接验收（2026-11-09）

已实现：V21连接表/平台枚举、V22Webhook密钥；只读OAuth1订单适配器（传输与签名地址分离）、可信地址白名单、企业管理员配置/AES-GCM密钥、SKU映射/当前企业回退、BigDecimal收入/商品退款、分页上限/超时重试、原始签名Webhook/业务载荷去重/Outbox权威重取及本地cron；同秒权威变化仍处理，重复状态无重复库存副作用。店铺筛选、仓库搜索和配置反馈已补齐。

实际验证：46项后端零失败/错误/跳过（woo-adapter-backend.log），单元4通过，原浏览器13通过58.1秒（woo-core-browser.log），真实Woo浏览器1通过5.1秒，十次同步一单/企业与角色拒绝；Woo实际十次回调一条处理事件/伪造签名401/库存无副作用；普通更新经cron自动投递通过；模拟付款状态、预占、发货10仅一运单、费用50/72、398元、确认10均通过。独立空库首次及重复建店、Chrome后台登录通过，临时项目/两个卷已清理。默认核心降级通过，当前运行核心5+Woo店铺/DB/cron3服务，数据保留。

新增CI woocommerce作业及35路径OpenAPI、启动/验收脚本、固定摘要镜像。新增作业尚未远程执行；此前511f1a4四作业成功是历史基线，不自动覆盖新代码。详见WOOCOMMERCE_INTEGRATION.md。没有真实付款/邮件/承运商发货或Woo写状态回传；测试订单付款状态为明确夹具。

过程修复：空商品初始化失败记录经条件核实移入回收站，未永久删订单或放松SKU校验；测试快照出现过本地凭据，已撤销旧ConsumerKey并轮换只读密钥，现表单提交即清空/连接toString及网络错误脱敏；Docker访问日志不含查询串，私有文件和测试结果均忽略。现有业务库与店铺数据未清空。