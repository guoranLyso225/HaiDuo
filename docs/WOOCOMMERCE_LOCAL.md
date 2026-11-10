# 本地WooCommerce测试店

最新状态：已完成海舵只读订单、签名Webhook、自动队列及本地履约利润联调。使用./infra/start-woocommerce-link.ps1，详见[完整联调报告](WOOCOMMERCE_INTEGRATION.md)。本文原先尚未接通的描述由该报告更新。

2026-11-09新增，独立Compose项目`haiduo-woo`。仅用于开发测试，无订阅、真实支付或自动发货配置。主项目仍为Java21/React；WordPress是外部平台测试环境，不替换海舵架构。

## 访问与凭据

- 店铺：http://localhost:8090/
- 管理后台：http://localhost:8090/wp-admin/
- 管理员：`haiduo_admin`
- 密码：根目录忽略文件`.woo.env`中的`WOO_ADMIN_PASSWORD`，不要上传或发到聊天。
- 商品API：http://localhost:8090/wp-json/wc/store/v1/products

后台可查看商品和订单。已初始化DEMO-BAG（100 USD、库存100）与一条无真实支付的待付款测试订单。Woo库存和海舵库存是两个独立系统，当前没有同步。

## 启动与停止

先启动Docker Desktop，从项目根目录运行：

```powershell
./infra/start-woocommerce.ps1
```

首次运行生成随机`.woo.env`，后续保留密码和数据库，勿重新生成已有环境的数据库密码。模板为`.woo.env.example`，不要覆盖现有.env或海舵演示密码。

停止测试店并保留数据：

```powershell
docker compose --env-file .woo.env -f infra/compose.woocommerce.yml stop
```

该项目新增2容器：`haiduo-woo-shop-1`和`haiduo-woo-db-1`，文件与数据库使用独立woo-files/woo-db卷。数据库不发布端口，店铺仅绑定127.0.0.1:8090；不依赖海舵8088。不要使用down -v清掉已有店铺数据。

## 固定版本与实际验证

版本由WordPress官方版本/插件API查询后固定：WordPress7.1.4/PHP8.3 Apache、WooCommerce11.2.2、MariaDB11.4.9；未用latest。已禁用自动更新以保持演示可复现，后续升级应主动选择并验收固定版本。

- Docker镜像下载与两服务启动通过（woocommerce-start.log）。
- 初始化及重复启动通过，保留已存在的SKU和种子订单（woocommerce-verification.log）。
- 真实Chrome后台登录和Store API商品检查通过（woocommerce-check.json）。公开商品接口不是已验证管理员订单REST API的替代声明。
- 初始化遇到WC命令行上下文及首次安装订单存储同步问题，已补齐WC初始化、同步及首次安装回调，随后后台浏览器复验通过；没有删库重建来掩盖问题。
- 未修改海舵业务代码，本轮未重跑其41项后端/13项浏览器基线。

官方资料：[WordPress镜像](https://hub.docker.com/_/wordpress)、[WooCommerce REST API](https://woocommerce.com/document/woocommerce-rest-api/)。

## 下一步：海舵连接器

**已接通海舵。** 详见联调报告；以下为最初建店阶段的设计背景。

需要新增WooCommerce连接器、店铺配置和凭据安全保存；接通订单分页/状态映射、SKU映射、Webhook签名与幂等，然后做两个真实本地系统的联调。后台REST API密钥从WooCommerce → Settings → Advanced → REST API生成，读取订单只申请Read权限。

WooCommerce管理员REST API身份验证在本地HTTP环境与HTTPS环境不同，不能简单把Shopify Client ID/Secret换成Woo密钥，也不要将密钥放URL后打印日志。后续实现应明确本地认证方案或为测试店配置HTTPS。Docker后端访问店铺也不能直接使用其自身的localhost，需单独配置可达地址。

真实Shopify联调仍标记待验证；WooCommerce通过不等同Shopify通过。公网域名、正式支付、真实客户和发布均未配置。
