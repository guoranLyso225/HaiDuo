# WooCommerce本地真实联调报告

2026-11-09。使用自建WordPress7.1.4、WooCommerce11.2.2、MariaDB11.4.9；Docker镜像固定摘要。它们是独立外部测试系统，海舵仍为Java21/React模块化单体。

## 已实现

- 新增WOOCOMMERCE店铺类型，连接仅企业管理员可配置；其他企业对象404、运营写连接403。服务端固定可信地址，客户端不能指定任意目标；同一来源地址不能跨店铺/企业重复绑定。
- Consumer Key/Secret及独立Webhook密钥使用现有AES-GCM加密保存；不通过读取接口回传。表单提交即清空，连接请求toString脱敏，网络错误不带签名URI。
- 只调用WooCommerce REST v3 GET接口。本地HTTP使用OAuth1 HMAC-SHA256；传输地址与站点签名地址分别配置，解决容器地址与WordPress Home URL不同的问题。公网连接要求HTTPS。
- 50单/页、最多20页及25秒分页预算；请求超时10秒、最多3次有界重试。超范围整次失败，不静默截断。未实现大店铺增量游标。
- 金额使用BigDecimal读取十进制字符串；净商品收入为折后商品行total之和，排除税、运费等；退款读取商品行明细扣除，无法分配的整笔退款明确失败，需人工核对。采购成本不因退款自动返还，退货入库/复杂财务冲回不在本适配器自动处理范围。
- 支付、退款和履约分别映射。completed视为平台已履约，processing/pending等保持独立付款与履约状态，不根据付款自动扣库存。
- 复用店铺商品ID映射，缺映射时仅在当前企业按SKU编码匹配。映射缺失导致事务失败，不绕过或跨企业查找。
- 同步通过原有Outbox/RabbitMQ/SyncProcessor，订单按企业/店铺/平台ID幂等；当前权威状态同秒有变化也处理，重复状态不重复释放库存，旧时间版本拒绝覆盖。
- Webhook先按绑定店铺密钥验证原始请求体HMAC，限制1MB；支持order.created/order.updated。Inbox去重与Outbox写入同事务，后台重新获取权威订单，不信任载荷金额。
- 重复载荷按企业、店铺、topic和SHA256去重，即使每次delivery ID不同也不重复处理。公开Webhook接口只有指定路径免浏览器CSRF，仍需有效平台签名。
- 独立本地cron profile处理WordPress队列，不要求前台有人访问。仅本地MU插件允许固定内部API回调地址，禁止本测试店发送真实邮件；该插件不得直接作为公网生产配置。

## 使用现有环境

1. 从根目录执行`./infra/start-woocommerce-link.ps1`。保留既有.env、.woo.env和.demo-password；TOKEN_ENCRYPTION_KEY必须是持久的32字节随机值Base64，已有密文后不得随意更换。本机已补齐该配置，值不写入文档。
2. 海舵http://localhost:8088，测试店http://localhost:8090/wp-admin/。WordPress管理员haiduo_admin，密码在忽略的.woo.env；海舵演示密码仍用原文件。
3. 在海舵选择企业管理员，店铺页创建WooCommerce店铺；已有本轮测试店则筛选Woo并选择CONNECTED那一行，不重复绑定同一来源。
4. 从本机忽略文件.woo-credentials.json复制consumerKey、consumerSecret及webhookSecret到连接表单，点击验证。不要将文件或值发送聊天/提交GitHub。
5. 配置履约仓库、USD、经确认的CNY/原币汇率及来源；点击读取订单，在同步任务页查看。修改配置只影响后续新单，历史订单仓库、成本和汇率快照保持原值。
6. 本地演练执行下面命令。它们仅操作本地演示数据；付款为状态夹具，物流LOCAL-MOCK，人工贡献确认不代表真实银行结算。

```powershell
./infra/verify-woocommerce-webhooks.ps1
./infra/verify-woocommerce-fulfillment.ps1
./infra/verify-woocommerce-webhooks.ps1 -QueueOnly
```

上述脚本会为已连接的本地店铺注册order.updated测试Webhook，十次发送验证去重；QueueOnly只更新测试订单，等待cron正常自动投递。后台REST密钥权限为read，注册Webhook由本地WordPress管理员初始化工具完成。

两个Woo主容器之外，联调模式增加`haiduo-woo-cron-1`，核心5个服务保持不变。只启动店铺仍可使用`./infra/start-woocommerce.ps1`；停止Woo及worker可用`docker compose --env-file .woo.env -f infra/compose.woocommerce.yml --profile webhooks stop`，保留数据。

## 实际验收证据

| 检查 | 本轮结果 |
|---|---|
| 完整后端集成 | woo-adapter-backend.log：46项，失败/错误/跳过均0，真实PG/Rabbit/pgvector及HTTP模拟限流/分页/签名/退款测试 |
| 原有浏览器回归 | woo-core-browser.log：13通过，58.1秒，覆盖原业务、搜索AI演示、PWA、移动端等 |
| Woo真实浏览器 | woo-live-browser.log：1通过，5.1秒；真实签名连接、十次同步一单、企业404/运营403、地址白名单拒绝 |
| 真实回调 | woo-live-webhooks.log：十次WordPress发送，一条新处理事件，库存无副作用，伪造签名401 |
| 自动队列 | woo-auto-webhooks.log：普通订单更新由cron自动投递成功，不直接调用发送方法 |
| 履约与利润 | woo-live-fulfillment.log：模拟付款状态回调、预占、十次发货仅一运单、费用50/72、398CNY、十次确认幂等 |
| 前端/配置 | 单元4项通过；镜像构建通过；YAML/PowerShell语法通过；OpenAPI解析35路径 |
| 从零建店 | woo-clean-check.json、woo-clean-initialize.log、woo-clean-repeat.log：独立空库首次/重复初始化、后台Chrome登录及单个示范商品通过，临时资源已清理 |
| 默认核心 | woo-final-core-check.log：数据库搜索回退、AI明确禁用、订单仍可用；Woo连接保留 |

本轮新增GitHub woocommerce作业，覆盖首次建店、浏览器连接、Webhook、履约利润及自动队列。本地等效检查已执行，新增作业尚未远程执行；此前提交511f1a4的四作业通过不代表这批新代码已远程验收。

## 处理过的问题与剩余边界

- 修复无初始加密密钥、容器URI与公开签名URI不一致、店铺分页定位、虚拟仓库下拉、同步配置提示被隐藏等问题；新增店铺筛选和仓库搜索。
- 初次建店失败遗留一条零金额空商品测试订单，已验证条件后移入回收站，未永久删除，也未通过忽略缺失SKU绕过业务校验。
- 本地失败快照曾包含测试密钥，已撤销旧Consumer Key并生成新只读密钥，提交时清空表单及错误脱敏已修复；私有凭据文件及test-results均忽略。
- 海舵履约不会回写Woo状态/运单，尚无Woo退款或付款写入接口；真实支付、承运商、复杂拆单、无法分配退款及大规模增量同步未验收。不能将本地测试声明为真实交易或Shopify联调通过。
- 海舵现有PostgreSQL/对象备份脚本不包含Woo的MariaDB和WordPress文件卷；本地测试数据可初始化重建，若要保存真实店铺数据，需另行制定Woo备份及密钥方案。
- 外部Shopify、真实S3/模型、正式部署仍需各自条件；默认核心不依赖它们，不自动购买或发布。

官方依据：[Woo REST API认证与订单接口](https://woocommerce.github.io/woocommerce-rest-api-docs/)、[Woo API密钥管理](https://woocommerce.com/document/woocommerce-rest-api/)。
