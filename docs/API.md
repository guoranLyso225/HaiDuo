# 阶段一接口与数据约定

API开发端口8084；Vite将/api代理到同源后端。JSON UTF-8。业务范围来自认证用户、服务端会话所选企业及实时成员验证，客户端不能指定任意tenant_id。

登录前GET /api/session/csrf取得token/headerName。所有POST在对应header中传token；登录使用application/x-www-form-urlencoded username/password，成功204。登录后重新取得CSRF令牌。Cookie为HttpOnly、SameSite=Lax，生产Secure默认true。

| 方法与路径 | 用途/权限 |
|---|---|
| GET /api/session/csrf | 匿名可获取令牌 |
| POST /api/session/login、logout | 会话登录/退出 |
| GET /api/session | 当前用户、成员关系与选择企业 |
| POST /api/session/tenant/{id} | 必须是该企业成员 |
| GET /api/products | SKU列表，企业范围 |
| POST /api/products | ADMIN/OPERATIONS；code/name/cost字符串 |
| GET /api/catalog/products/{id} | JPA企业范围Product详情 |
| GET/POST /api/stores | 企业店铺；创建限ADMIN/OPERATIONS，当前MOCK |
| GET/POST /api/stores/{id}/listings | 平台商品与SKU映射；写入限ADMIN/OPERATIONS |
| GET /api/orders | 企业订单，可按status/search筛选 |
| GET /api/orders/{id} | 商品/快照/时间线/费用/运单 |
| POST /api/orders/import | csv文本、可选warehouseId；ADMIN/OPERATIONS |
| POST /api/orders/{id}/reserve、cancel | ADMIN/OPERATIONS，订单行锁和事务 |
| POST /api/orders/{id}/shipment | ADMIN/WAREHOUSE；carrier/trackingNumber |
| GET /api/inventory | warehouseId/search筛选 |
| GET /api/inventory/movements | 企业入库与订单流水，最近200条 |
| POST /api/inventory/receipts | ADMIN/WAREHOUSE；requestKey/skuId/warehouseId/quantity |
| GET/POST /api/warehouses | 创建限ADMIN |
| GET /api/shipments | 企业运单 |
| POST /api/orders/{id}/fees | ADMIN/FINANCE；kind、amount、currency、rate、source |
| GET /api/profits | ADMIN/FINANCE，CNY预估贡献 |
| GET /api/dashboard | ADMIN/FINANCE；days/store，UTC区间 |
| GET /api/members、POST /api/members/role | ADMIN，保留最后管理员 |
| GET /api/audit | ADMIN，最近200条 |

商品/订单/库存分页参数page从1开始，size为1..200，默认200；返回数组及X-Total-Count。其他列表仍有各自上限，不能解释为全量导出。

CSV固定头store,order_no,sku,quantity,net,currency,rate；最多500行/100000字符，同订单可多行SKU，每行重复整单净收入/币种/汇率，不累加金额；重复SKU需先合并数量。导入同一事务，重复企业/店铺/订单号跳过。字段内逗号、引号当前不支持。未知SKU/店铺400，外企业对象404或403。

金额以十进制字符串传输，Java BigDecimal/数据库NUMERIC。费用保存原币/币种/汇率/来源/生效UTC时间，CNY折算保留4位HALF_UP；贡献最终保留2位。费用按类别覆盖编辑并审计，始终为ESTIMATED，尚不支持结算锁定。利润=净收入×订单汇率快照－采购快照×数量－已录直接费用。缺成本订单排除，未录费用按0预估；不含广告、固定开支、工资和税。

错误：401未登录；403权限或CSRF；404对象不存在或不属本企业；409库存不足/状态冲突/幂等键冲突；400参数错误。业务异常JSON含message，不含敏感请求体。未覆盖的基础设施异常仍需后续统一关联标识治理。

库存支持整单、单履约仓库，不跨仓借货；预占、取消释放、发货扣减均事务执行。requestKey为入库幂等键；页面按用户/企业持久保留未确认的重试键，刷新后可恢复。

## 阶段二接口

以下内部写入仍需服务端会话、已验证成员关系和CSRF头。金额/汇率参数为十进制字符串，禁止传浮点数计算结果。

| 接口 | 权限与行为 |
|---|---|
| GET /api/sync | ADMIN/OPERATIONS；outbox和jobs各最近100条，租户限定 |
| POST /api/sync/{eventId}/replay | ADMIN；DEAD或未完成的失败消费可重放；处理中/已处理409，跨企业404 |
| POST /api/stores/{id}/sync | ADMIN/OPERATIONS；事务排队后返回QUEUED，并非已同步；Shopify未授权/未配置409 |
| POST /api/stores/{id}/sync-settings | ADMIN/FINANCE；warehouseId、currency、rate、source；仓库需同企业，CNY汇率必须1，只影响新订单 |
| POST /api/shopify/{storeId}/authorize | ADMIN；body shop为完整myshopify.com域名，返回只读订单OAuth URL；缺凭据503 |
| GET /api/shopify/callback | 原认证会话；一次性state、服务端actor/成员、HMAC验证；成功保存加密令牌 |
| POST /api/webhooks/shopify | 无会话/CSRF，必须原始请求体HMAC；绑定店铺、topic、Webhook-Id/Event-Id校验；可靠落库202；未配置503、签名/未绑定401 |
| POST /api/products/{skuId}/image | ADMIN/OPERATIONS；multipart字段file，PNG/JPEG，1..2097152字节，尺寸不超4096；返回id/url |
| GET /api/files/{id} | 认证成员；同企业授权文件，私有no-store；跨企业404 |
| GET /healthz | 无认证；仅显示DB/Redis就绪与否，不披露配置；容器内部8080，前端/healthz仅静态存活 |

创建店铺POST /api/stores额外支持platform=MOCK或SHOPIFY；后者初始DISABLED。产品列表imageUrl为空或受保护的/api/files路径。订单增加sync_review_required、platform_fulfillment_status、externally_fulfilled字段。MOCK是固定样例；SENT只表示Broker已确认，不等于消费者业务完成，应查看jobs的COMPLETED/FAILED。

Webhook只订阅orders/create、orders/updated、orders/cancelled、orders/paid、refunds/create；未支持topic验签绑定后202忽略。需在真实Shopify应用中配置HTTPS回调及订阅，当前未自动创建订阅。后台重取权威订单并按updatedAt合并。复杂履约需要人工核对，不重复扣本地库存。接口限额与未验证外部依赖见PHASE_TWO_REPORT.md。
## 阶段三接口

所有AI和搜索业务接口均需认证成员与当前企业；写入还需CSRF。内部请求不接受tenant_id作为授权依据。

| 接口 | 行为 |
|---|---|
| GET /api/search?q=关键词 | 最多50条；返回engine=OPENSEARCH或DATABASE与items；企业独立索引再经主库二次校验 |
| POST /api/search/reindex | ADMIN重建当前企业最多5000商品索引；关闭增强搜索503 |
| GET /api/ai/status | mode为DISABLED、FIXED_DEMO或MODEL；返回额度与输入上限 |
| GET /api/ai/knowledge | 当前企业文档清单，最多50份 |
| POST /api/ai/knowledge | ADMIN/OPERATIONS；title不超200、text不超12000，支持文本/Markdown；切分并生成向量 |
| POST /api/ai/ask | question不超4000；返回answer、mode、sources(documentId/title/chunk)；资料不足明确返回 |
| POST /api/ai/drafts | ADMIN/OPERATIONS；skuId与instructions不超4000；只保存PENDING_REVIEW，不自动发布 |

默认无模型明确503；AI_DEMO必须显式开启，固定演示算法及摘录不冒充真实模型。每日UTC100模型调用额度在独立事务预扣，导入按切分数量计、问答预扣2、草稿1；失败不返还。模型调用连接3秒、读取10秒、外层15秒，不自动重试，基础设施故障502、额度429。真实模型配置与增强profile见PHASE_THREE_REPORT.md。

监控仅容器内部9090的/actuator/health与/actuator/prometheus，业务8080无匿名指标入口；OTLP发送到独立Collector。前端仅代理/api，未公开监控路径。

## 全量验收补齐接口

| 接口 | 行为 |
|---|---|
| GET /api/roles | ADMIN；四类固定角色与显式权限清单；首版不在线自定义角色策略 |
| POST /api/shipments/{id}/events | ADMIN/WAREHOUSE；eventKey(UUID)、version(正整数)、status(IN_TRANSIT/DELIVERED/EXCEPTION)、occurredAt(UTC)；仅MOCK，重复键同内容返回duplicate，不同内容409 |
| GET /api/shipments/{id}/events | 当前企业成员；最多200条，跨企业404；旧版本不应用，DELIVERED终态不回退 |
| GET /api/exchange-rate-snapshots | ADMIN/FINANCE；最近200条独立不可变快照，金额为字符串；无修改/删除接口 |
| POST /api/orders/{id}/profit-confirmations | ADMIN/FINANCE；requestKey、source。已付款/已发货/无退款且采购成本/运费/平台费完整后，追加人工确认实际贡献版本；同键幂等，不修改旧版本 |
| GET /api/profit-confirmations | ADMIN/FINANCE；当前企业最近200个人工确认版本；不等同自动平台结算 |
| GET /api/orders/export.csv | ADMIN/OPERATIONS/FINANCE；默认近30天，store/status/from/to(UTC)可过滤；最多5000条，最大366天；仅当前企业，CSV防公式注入，no-store |

V17模拟轨迹，V18独立汇率快照（订单与费用复合租户外键），V19不可变实际贡献版本，V20幂等触发器。订单/费用快照创建与业务变动同事务；重复订单不会新建多余快照。人工确认只固化该时点已核对数据，之后变动应追加确认版本，历史不被覆盖。
POST /api/products/{productId}/skus：管理员/运营在当前企业商品下新增SKU，body同创建商品code/name/cost；每SKU独立成本/库存、同商品共享图片。创建商品/SKU通过Outbox增量更新已启用的搜索索引；关闭搜索期间仍可业务创建，重新启用后可手动重建。复杂规格组合编辑器不属于首版必需功能。


## 知识库索引维护

管理员GET /api/ai/knowledge/index查看配置与片段数，POST /api/ai/knowledge/index/rebuild（CSRF）用当前企业主库已有向量重建独立索引，返回indexedChunks/modelCalls=0。未配置向量库503；操作不调用模型，更换模型仍需重新向量化。角色、事务和验证见[维护说明](KNOWLEDGE_INDEX_MAINTENANCE.md)。

## WooCommerce只读连接与Webhook

新增店铺类型WOOCOMMERCE，管理员GET /api/woocommerce/status读取服务器可信地址；POST /api/woocommerce/{id}/connect提交baseUrl、consumerKey、consumerSecret及可选webhookSecret。密钥不回传，连接请求需会话/CSRF，跨企业对象404、非管理员403；可信源地址唯一绑定，避免同站点跨企业共享。请求失败不记录认证URI，表单提交后清空。

POST /api/webhooks/woocommerce/{store}不依赖浏览器会话，必须用绑定的Webhook密钥对原始body签名，支持order.created/order.updated，限制1MB，202可靠入队/去重。后台读取权威订单；业务载荷去重、同秒状态变化处理及金额口径见WOOCOMMERCE_INTEGRATION.md。海舵只读平台订单，不回写支付、状态或运单。

订单详情增加warehouse_id和明细sku_id，均限定认证企业，用于展示实际履约仓库及恢复演练。