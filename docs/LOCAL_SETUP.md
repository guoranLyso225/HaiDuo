# 本机检查与恢复

## Java / IDEA
POM 的 java.version 为 21；.idea/misc.xml 的 languageLevel 为 JDK_21、SDK 名称为 21。名称不能证明 SDK 路径正确。

IDEA 设置：
1. File → Project Structure → Project：SDK 选择 `D:\Java\Azul Zulu jdk21.0.4`，语言级别 21；Modules 的 SDK 选择 Project SDK。
2. Settings → Build, Execution, Deployment → Build Tools → Maven → Importing：JDK for importer 选择 Project SDK。
3. Maven → Runner：JRE 选择 Project SDK。Maven User settings file 勾选 Override 并指定 `D:\HaiDuo\infra\maven-settings.xml`，本地仓库为 `D:\HaiDuo\.m2`，然后 Reload All Maven Projects。
4. Spring Boot/Application 运行配置的 JRE 同样选择 21。IDEA Terminal 默认继承系统环境，需要单独设置 JAVA_HOME 后重新打开终端。

PowerShell：
```powershell
$env:JAVA_HOME='D:\Java\Azul Zulu jdk21.0.4'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
java -version
mvn -version
mvn -B -U -s infra/maven-settings.xml -f services/api/pom.xml clean verify
npm.cmd ci --prefix apps/web --registry=https://registry.npmjs.org
npm.cmd run build --prefix apps/web
```
不要只检查 java -version；mvn -version 也必须显示 21。持久设置请在 Windows 用户环境变量配置 JAVA_HOME，并把 `%JAVA_HOME%\bin` 放在旧 Java 路径前；无需删除旧 JDK。

## 网络
本次受限执行报 npm connect EACCES、Maven Permission denied: getsockopt；获准网络执行后均能下载，不能据此判断机器断网。外部终端若仍失败，检查防火墙或代理能否访问 registry.npmjs.org、repo.maven.apache.org 的 HTTPS；企业代理应配置 Maven settings 的 proxies 和 npm proxy/https-proxy，勿提交凭据或关闭 TLS 校验。npm 用户配置原指向 npmmirror，以上命令显式使用官方源。

## Docker / PostgreSQL
本次未找到 docker、psql、pg_isready 命令，未发现对应服务或常见安装目录；localhost:5432、6379 均连接失败。非标准位置安装仍可能存在，需要自行核对。

建议安装 Docker Desktop（Windows 的 WSL2 后端），启用 BIOS 虚拟化和 WSL2，启动 Docker Desktop 并切换 Linux containers。新终端执行 `docker version`、`docker compose version`、`docker info`，需要服务端也成功返回。安装和系统重启需用户操作。

若不用 Docker，安装 PostgreSQL 服务端和命令行工具，创建数据库 haiduo 及独立用户，设置 DB_PASSWORD/DATABASE_URL；执行 `pg_isready -h localhost -p 5432` 和 `psql -h localhost -U haiduo -d haiduo -c "select version();"` 验证。Redis 也需独立安装或容器运行，`redis-cli ping` 应返回 PONG。仅开放端口不等于认证可用。

目前没有 Compose，也没有数据库集成测试；安装 Docker 后仍需在阶段一补充 PostgreSQL/Redis 开发环境与真实数据库测试，不能直接宣称完整应用可启动。
