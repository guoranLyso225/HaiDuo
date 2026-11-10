# 海舵 HaiDuo

中文多租户跨境运营学习与求职展示项目。首版本地开发及验收完成：Java21 / Boot4.1.2 / Modulith2.1.1 / Spring AI2.0.2，后端40项真实集成、前端4项单元、浏览器11项通过。真实Shopify/S3/模型、远程CI及生产部署仍需要外部条件，未声明已完成真实联调。

支持多企业/四角色、会话登录/CSRF、多SKU商品、模拟店铺/映射、多行CSV订单、多仓预占/发货/流水、模拟轨迹、不可变汇率与人工实际贡献、看板、审计、导出、Outbox/同步、搜索、知识库、PWA和监控。

当前结论：[首版交付报告](docs/COMPLETION_REPORT.md) · [验收矩阵](docs/FINAL_ACCEPTANCE_MATRIX.md) · [实施记录](docs/IMPLEMENTATION_PLAN.md)。历史阶段报告不是当前验收结果。

## 完整容器演示（阶段二）

复制.env.example为.env，设置DB_PASSWORD、RABBIT_PASSWORD、DEMO_PASSWORD（至少12字符），本机HTTP设置COOKIE_SECURE=false。保留已有.env及已有演示密码；重启不会覆盖账号密码。Docker Desktop须使用Linux容器。

Windows PowerShell和Linux/macOS均从根目录运行：

```sh
docker compose --env-file .env -f infra/compose.yml build
docker compose --env-file .env -f infra/compose.yml up -d --wait --wait-timeout 90
docker compose --env-file .env -f infra/compose.yml ps
```

访问 http://localhost:8088 。命令有限等待健康状态，服务在后台运行；先用ps和端口检查避免重复启动。停止演示用 `docker compose --env-file .env -f infra/compose.yml stop api web`，数据库与文件卷保留，不执行down -v。默认五服务复用haiduo-dev项目已有数据卷。首次拉取/构建需要Docker Hub、Maven Central、npm网络；失败查看实际构建日志，不手工改迁移校验值。

阶段二演示：商品上传PNG/JPEG → 店铺mock“读取订单” → 同步任务查看STORE_SYNC完成 → 订单查看MOCK-SYNC-001。所有MOCK均为固定样例。Shopify连接先创建SHOPIFY店铺、配置仓库与确认的汇率，配置.env中Shopify凭据和HTTPS回调后生成授权链接；真实联调未执行。S3设置STORAGE_BACKEND=s3及注释所列凭据，默认local持久卷无需外部账号。

后端回归（PowerShell）：

```powershell
$env:RUN_DATABASE_TESTS='true'; $env:RUN_TESTCONTAINERS='true'
$env:RUN_MESSAGING_TESTS='true'; $env:RUN_SYNC_TESTS='true'
mvn -s infra/maven-settings.xml -f services/api/pom.xml '-Dtest=InventoryContainerTest,MessagingContainerTest,SyncContainerTest,ShopifyAdapterTest,BlobContractTest,ProfitTest,RequestCorrelationTest' test
$env:E2E_BASE_URL='http://localhost:8088'
Set-Location apps/web
npx.cmd playwright test
Set-Location ../..
```

浏览器命令建议先cd apps/web再npx playwright test，密码文件按该工作目录读取。Linux/macOS将环境变量改用export，npm.cmd改npm；命令说明已提供，该平台未实际执行。故障验收脚本 `./infra/verify-stage-two.ps1` 从根目录执行，会在本地演示库创建唯一故障探针、临时约束并停止/恢复Rabbit及替换API；仅对本地演示环境运行。完整边界与证据见[阶段二报告](docs/PHASE_TWO_REPORT.md)。
## 分进程开发运行

需要Java21、Maven、Node22.12+或Node24、Docker Desktop Linux引擎。命令从仓库根执行。保留已有.env；新环境复制.env.example并设置数据库密码。

```powershell
docker compose --env-file .env -f infra/compose.dev.yml up -d --wait
$env:DB_PASSWORD=(Get-Content .env | Where-Object { $_ -like 'DB_PASSWORD=*' }).Substring(12)
$env:DEMO_PASSWORD=Read-Host '设置至少12字符的演示密码'
mvn -s infra/maven-settings.xml -f services/api/pom.xml test package
java -jar services/api/target/api-0.1.0.jar --server.port=8084 --spring.profiles.active=demo --server.servlet.session.cookie.secure=false
```

新终端：

```powershell
npm.cmd ci --prefix apps/web --registry=https://registry.npmjs.org
npm.cmd run dev --prefix apps/web -- --port 5173 --strictPort
```

访问http://localhost:5173。API和Vite是常驻服务，就绪即可操作，Ctrl+C关闭；启动前检查端口避免重复启动。仅本机HTTP关闭Secure Cookie；生产HTTPS保留安全默认值。

账号demo1.admin、demo1.operations、demo1.warehouse、demo1.finance，企业2为demo2前缀。密码由首次demo启动环境变量决定，重启不覆盖。非demo环境不初始化演示账号。

Linux/macOS：设置JAVA_HOME、DB_PASSWORD、DEMO_PASSWORD，使用同样Maven/Java/Compose命令；npm.cmd改为npm。Maven设置使用根目录.m2。该平台命令未实际执行。

## 演示路线

登录并选企业1 → 创建成本200的SKU → 库存入库10 → CSV导入 → 预占 → 输入承运商/运单发货 → 利润录入运费50、平台费72 → 查看398.00CNY → 详情和审计。

```csv
store,order_no,sku,quantity,net,currency,rate
mock,MY-DEMO-001,DEMO-BAG,1,100.00,USD,7.2
```

替换唯一订单号和已有SKU。支持同单多行SKU，每行须重复相同整单净收入/币种/汇率，不累加金额；同单SKU需合并数量。不支持引号/字段内逗号/复杂拆单。多仓先创建仓库，向对应仓库入库，再选该履约仓库导入。

## 检查

```powershell
$env:RUN_DATABASE_TESTS='true'
$env:RUN_TESTCONTAINERS='true'
mvn -s infra/maven-settings.xml -f services/api/pom.xml '-Dtest=InventoryContainerTest' test
npm.cmd run build --prefix apps/web
```

浏览器测试：在apps/web运行npx playwright install chromium及npx playwright test；先启动服务，密码保存于根目录忽略文件.demo-password。可用PLAYWRIGHT_CHROMIUM_EXECUTABLE指定已有浏览器。测试创建演示记录，不清空已有数据。

HTTP脚本infra/verify-auth.ps1和verify-order-isolation.ps1接受Password参数。所有通过结论以实施记录的实际日志为准。

## 外部依赖与扩展边界

本地首版无待修复的阻塞项。真实外部凭据/HTTPS回调、调用费用和部署权限仍需配置；不自动开通、购买或发布。复杂拆单、真实承运商、Amazon、自动平台对账、完整英文UI为明确扩展边界，详见交付报告。保留中英文基础资源和连接器接口。
## 第三阶段增强演示

运行profile、环境变量、真实模型依赖及边界见[阶段三报告](docs/PHASE_THREE_REPORT.md)，实际性能见[实测报告](docs/PHASE_THREE_PERFORMANCE.md)。核心五服务默认不依赖增强服务。商品创建/新增SKU通过Outbox更新当前企业搜索索引，也支持页面手动重建；知识库固定演示必须显式AI_DEMO=true，真实模型默认禁用。CI配置本地等效检查与GitHub远程执行分开记录。

## 完整后端验收（不要遗漏测试开关）

PowerShell，从根目录运行，先确认Docker Desktop Linux Engine正常。JAVA_HOME须为Java21；IDEA Project SDK、Maven importer与runner JRE也选21。

```powershell
./infra/verify-backend.ps1 -JavaHome $env:JAVA_HOME
# 脚本启用真实PG/Rabbit/向量容器，并拒绝失败、错误或跳过的测试。
# 空库演示数据检查使用独立测试容器，不清空现有库；RunId每轮取不同值：
./infra/verify-clean-bootstrap.ps1 -RunId demo-check-001
```

Linux/macOS等效命令（已提供，未在对应宿主实测）：

```sh
RUN_TESTCONTAINERS=true RUN_DATABASE_TESTS=true RUN_MESSAGING_TESTS=true RUN_SYNC_TESTS=true \
mvn -B -s infra/maven-settings.xml -f services/api/pom.xml \
'-Dtest=InventoryContainerTest,MessagingContainerTest,SyncContainerTest,ShopifyAdapterTest,BlobContractTest,ProfitTest,RequestCorrelationTest,KnowledgeContainerTest,VectorContainerTest,ModelAdapterTest,TrackingSnapshotContainerTest,ArchitectureTest,RoleTest' verify
```

普通`mvn verify`不启用集成测试，构建成功不代表数据库验收通过。浏览器需先执行`npx playwright install chromium`；Windows也可在apps/web设置`$env:PLAYWRIGHT_CHROMIUM_EXECUTABLE='C:/Program Files/Google/Chrome/Application/chrome.exe'`后执行`npx.cmd playwright test`。最终本机使用Chrome完成11项验收。

## 完成范围与监控告警

本地首版与真实外部验收分开记录：[还差什么才算完成](docs/REMAINING_WORK.md)。新增两条本地Prometheus告警规则及可复现规则测试，见[告警排查手册](docs/MONITORING_RUNBOOK.md)；未配置外部通知，远程CI作业尚未执行。


## 知识库索引维护

AI助手中管理员可重建当前企业已有向量索引并清理孤立记录，不调用模型；默认未配置向量库时明确禁用。实现、边界和实际41/4/13项回归见[维护说明](docs/KNOWLEDGE_INDEX_MAINTENANCE.md)。


## 备份与隔离恢复

默认本地存储可执行 ./infra/verify-backup-restore.ps1 -RunId 唯一标识（只允许小写英文、数字及横线）。会短暂停API，自动恢复；备份敏感数据不提交。33表/19文件及恢复应用授权访问已实测，见[备份恢复手册](docs/BACKUP_RESTORE.md)。CI步骤已配置，远程未执行。


## 免费本地WooCommerce测试店

执行 ./infra/start-woocommerce.ps1，访问http://localhost:8090/wp-admin/，管理员haiduo_admin，密码位于忽略的.woo.env。两容器和数据卷独立于海舵；已实现只读订单、Webhook、自动队列及本地履约利润联调。见[本地店铺说明](docs/WOOCOMMERCE_LOCAL.md)。


## WooCommerce免费本地联调

一键启动./infra/start-woocommerce-link.ps1，海舵8088、店铺8090。ConsumerKey/Secret和Webhook密钥仅保存在忽略的.woo-credentials.json；配置、测试夹具边界及46项后端/13项原浏览器/1项Woo浏览器、自动回调和398元闭环证据见[联调报告](docs/WOOCOMMERCE_INTEGRATION.md)。新增CI作业待本轮代码推送后远程验收。
