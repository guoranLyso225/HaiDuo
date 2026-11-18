# 提交与远程验收

当前工作区包含多批尚未提交的审批、编辑与纠错功能。先检查本地结果，再提交整个功能集，避免只提交前端而遗漏新Java文件或V23—V29迁移。

## 1. 提交前检查

在项目根目录运行，Windows PowerShell、Linux/macOS终端均可：

```sh
node infra/check-release.mjs
git status --short
git --no-pager diff --stat
```

脚本只读检查Git已跟踪及未忽略的新文件：私有配置文件/产物目录、常见凭据格式、交付文件、连续迁移版本，以及暂存前后的差异格式。它不修改Git配置，不暂存、提交或推送；发现疑似凭据只显示文件名，不输出内容。该检查不代表完整安全审计或真实平台验收。

`.env`、`.demo-password`、`.woo.env`、`.woo-credentials.json`、本地数据卷、备份和测试产物应保持忽略；`.env.example`及锁文件需要提交。不要使用`git add -f`强行加入忽略文件。已应用的迁移不要改写，有新变化应新增迁移。

## 2. 暂存并审核

确认本次工作区都是准备提交的更改后：

```sh
git add apps services infra docs .github README.md
git --no-pager diff --cached --stat
git --no-pager diff --cached --name-only
node infra/check-release.mjs
```

检查新Java类、前端组件、测试、迁移、接口文档和CI配置是否一起包含。`--no-pager`避免输出停留在`(END)`；普通分页显示时按`q`退出。

## 3. 提交并推送

```sh
git commit -m "feat: 完善海舵分级审批与业务编辑纠错"
git push origin HEAD
```

以上命令由你执行；本轮开发没有自动提交或推送。推送失败时保留本地提交，解决连接问题后再次`git push origin HEAD`即可，不需要重复提交。

## 4. 核对GitHub Actions

进入仓库Actions页面，打开**刚推送的提交**对应的verify运行。应核对frontend、backend、browser、monitoring、woocommerce各作业。frontend新增提交文件检查，backend包含全部新增集成测试，browser自动发现新增浏览器用例。

本地验收不能代替远程CI。失败时保存作业名、首个错误、提交SHA及相关测试日志；修复后创建新提交，再核对新提交的运行。不要把旧提交的绿色结果视为新代码已经通过。

后续Shopify、真实模型、真实S3及生产部署继续按外部依赖清单配置；远程CI中的固定演示和模拟契约不代表这些真实服务联调完成。

## Docker Hub下载镜像时返回500

若启动日志最早出现 `registry-1.docker.io` 的 `500 Internal Server Error`，是镜像仓库/网络阶段失败。可以在Actions重跑失败作业；使用本轮改进则需提交并推送新脚本。新脚本将镜像构建与启动分开，对明确的暂时网络故障最多重试三次，编译失败不重试。若持续失败，查看启动步骤最早的错误及 `services/api/target/woo-link-diagnostics/image-build.log`；容器尚未创建时，PostgreSQL未运行是后续结果。不要将旧提交的重新运行当作已应用新修复。

本地可运行 `powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-compose-phase.ps1` 验证重试策略；Linux/macOS安装PowerShell后使用 `pwsh -NoProfile -File infra/verify-compose-phase.ps1`，本轮Linux执行待新CI验证。诊断使用 `infra/diagnose-woocommerce-link.ps1`，数据库未运行时跳过SQL。
