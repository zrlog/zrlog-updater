# ZrLog Docker updater

独立 HTTP 服务，接收后台升级任务，使用 Docker Compose 拉取并替换当前项目的 `zrlog`
服务。运行时、后台及发布流程已接入源码。已有独立预览镜像可用于部署验证；正式使用仍需
对应应用集成和完整 Compose 站点验收。

## 独立预览镜像

维护者可通过本仓库 `Build and publish updater` 工作流指定源码 `ref`，设置
`push_image=true`，独立构建并发布 updater。push/PR 默认只构建验证。
工作流在 AMD64、ARM64 原生 Runner 上执行真实 Docker 测试、Native HTTP/重启冒烟、
容器检查，再推送 Docker Hub 与阿里云，验证两边的 digest、签名及匿名拉取。

预览标签为 `1.0.0-preview.<源码短提交>.<运行编号>.<重试次数>`，实际值见工作流摘要和
`updater-preview-publication-*` 证据。两个仓库使用相同标签：

- `zrlog/zrlog-updater:<预览标签>`
- `registry.cn-chengdu.aliyuncs.com/hibegin/zrlog-updater:<预览标签>`

将完整引用填入 `.env` 的 `ZRLOG_UPDATER_IMAGE`。此流程不推进应用更新清单或正式版本；
使用后台升级功能还需要包含对应 base/admin 集成的应用版本和已发布的应用镜像清单。

独立仓库已发布预览标签 `1.0.0-preview.1ef3c8e7cd37.38146772016.1`。
Docker Hub 和阿里云均已公开，支持 `linux/amd64`、`linux/arm64`。
[镜像构建及发布记录](https://github.com/zrlog/zrlog-updater/actions/runs/38146772016)包含两个架构各
22 项测试、Native 冒烟、镜像身份及公开拉取验证，镜像任务全部通过。
中国地区可配置：

```dotenv
ZRLOG_UPDATER_IMAGE=registry.cn-chengdu.aliyuncs.com/hibegin/zrlog-updater:1.0.0-preview.1ef3c8e7cd37.38146772016.1
```

可在同一入口设置 `push_image=false`、`publish_packages=false`、
`verify_version=<已发布标签>`，并将 `ref` 指向该镜像在本仓库的完整源码 SHA，执行双架构
匿名拉取、版本/源码身份和 Native HTTP 复验。此模式不会重新构建或改写镜像。
拆仓前的历史标签继续保留，其源码身份属于当时的主工程；新标签使用本仓库的提交。

## 构建

此 POM 独立于博客主工程父 POM，只引入 Gson；HTTP 服务使用 JDK 的 `jdk.httpserver`。
构建需要 JDK 21，Native 构建需要对应 GraalVM 和本机 C 工具链。

```bash
./mvnw -B test package
./mvnw -B -Pnative package
bash scripts/smoke-native.sh target/zrlog-updater
docker build -t zrlog-updater:local .
```

仓库自带 Maven Wrapper。JVM 包仅用于开发、调试和无 GraalVM
环境验证。容器运行 Native 可执行文件，以及实际控制 Compose 所需的 Docker/Compose
客户端；不包含后台、安装器、博客页面、主题或数据库模块。

元数据配置来自包内 `build.properties`，沿用 `mirrorWebSite` 与
`updateVersionJsonFilename`。构建时可显式指定：

```bash
./mvnw -Pnative package \
  -DmirrorWebSite=https://dl.zrlog.com/ \
  -DupdateVersionJsonFilename=last.docker-image.version.json
```

应用 `build.properties` 的 `dockerUpdateVersionJsonFilename` 必须与 updater 的
`updateVersionJsonFilename` 相同，两端 `mirrorWebSite` 也必须一致。应用原有
`updateVersionJsonFilename` 留给 ZIP/WAR，因同一 Native 程序可在 ZIP 和 Docker 中运行。
这些是构建资源配置；修改源文件后需要重新构建对应程序，后台不提供元数据地址输入框。

定制构建须让应用检查端与 updater 执行端采用同一元数据配置。HTTP API 不接受临时
元数据地址或任意镜像、容器、路径、命令。默认只从配置的元数据服务读取清单，失败
不会自动转到 GitHub latest。

## Compose 配置

使用本仓库的 `docker-compose.updater.yml` 作为原 Compose 文件的附加配置。将它和
原 Compose 文件、原 `.env` 放在同一部署目录。在原 `.env` 中补充：

```dotenv
# 必须是既有项目名；不要为了启用 updater 改项目名。
COMPOSE_PROJECT_NAME=your-existing-project
ZRLOG_DEPLOYMENT_DIR=/absolute/path/to/deployment
ZRLOG_UPDATER_TOKEN=replace-with-at-least-32-random-url-safe-characters
# 开发时可使用上文构建的本地镜像；正式使用已发布的完整镜像引用。
ZRLOG_UPDATER_IMAGE=zrlog-updater:local
```

首次接入需要用原项目名执行：

```bash
docker compose -f docker-compose.yml -f docker-compose.updater.yml up -d
```

主工程新的默认 Compose 已直接包含 updater，使用那份配置时只需 `docker compose up -d`，
并设置 `ZRLOG_COMPOSE_FILES=docker-compose.yml`，不再叠加此文件。默认应用 3.9.2 尚不包含
后台 Docker 升级集成，后台功能需使用包含对应 base/admin 集成的应用镜像。

目录必须按宿主机绝对路径挂入 updater 的相同路径，Compose 才能正确解释已有相对
挂载。自定义多个 Compose 文件时，通过 `ZRLOG_COMPOSE_FILES` 填写按相同顺序排列的
逗号分隔文件名。其列表必须匹配启动该项目使用的配置。源配置与现有容器的 Compose
配置 hash 不一致时停止升级；Docker/Compose 客户端版本变化造成 hash 差异时，应先用
一致版本协调部署。updater 的 HTTP 端口只在内部网络可用，不映射到宿主机。
站点对外端口必须固定，不能使用随机分配或端口范围；使用命名卷或显式绑定目录保存数据。

`ZRLOG_IMAGE`、`ZRLOG_UPDATER_IMAGE`、`MYSQL_IMAGE` 都接受完整仓库引用。应用镜像
可选择现有阿里云分发 `registry.cn-chengdu.aliyuncs.com/hibegin/zrlog:<version>`；updater
只在权威清单声明的仓库中选择当前平台。updater、MySQL 也必须选用该环境实际可访问的
来源。正式发布流程会生成 `zrlog/zrlog-updater:<version>` 和
`registry.cn-chengdu.aliyuncs.com/hibegin/zrlog-updater:<version>`；仅使用已经发布的版本。
updater 的正式镜像可处理 release/preview 两种应用升级，不需要另启一个预览 updater。

updater 需要 Docker socket 管理权限，以及部署目录写权限以原子更新 `.env` 中的
`ZRLOG_IMAGE`。Docker socket 具备宿主机容器管理能力，令牌仅供受信的 ZrLog 后台使用。

## HTTP 契约

除 `GET /health` 外，所有接口要求 `Authorization: Bearer <ZRLOG_UPDATER_TOKEN>`。
写请求要求 `Content-Type: application/json`，最大 4096 字节。所有响应禁止缓存。

| 请求 | 响应 |
| --- | --- |
| `GET /v1/capabilities` | Docker 与配置能力、当前仓库/平台、元数据来源；检查失败返回明确错误。 |
| `GET /v1/settings` | release 清单的可选仓库与已保存选择。 |
| `GET /v1/settings/preview` | preview 清单的可选仓库与已保存选择。 |
| `PUT /v1/settings` | 保存 `{ "repository": "完整仓库", "channel": "release" }`；空仓库沿用当前部署，不触发升级。 |
| `POST /v1/upgrades` | 接收 `{ "requestId": "唯一请求ID", "channel": "release", "version": "目标版本", "buildId": "目标构建ID" }`，返回 `202` 和任务。 |
| `GET /v1/upgrades/{requestId}` | 查询任务阶段、目标、结果及恢复提示。 |

任务 ID 为 16–80 位字母、数字、`_`、`-`。同一 ID 重试返回原任务；同一 ID 改变目标返回
409。同时只执行一个任务，其他提交返回 409。终态为 `succeeded`、`failed` 或
`interrupted`。所有阶段写入命名卷 `updater-state`，进程重启后的未完成任务标记为
interrupted，不盲目重新替换容器。

镜像下载成功且标签中的版本、源提交与清单一致后，才开始替换。更新 `.env` 后调用
Compose `up --no-deps --no-build --pull never --wait zrlog`，MySQL 不参与重建。验证同时
检查实际镜像 ID、容器健康状态和程序 `--version` 输出；它不等同于数据库功能验收。
写入前用 Compose 解析并比较完整配置，只有 `zrlog.image` 可以变化。`.env` 的多行
引号值、注释和原换行保留；不要在其他服务或其他设置中复用 `ZRLOG_IMAGE`，否则升级会
在改写配置前停止。
替换阶段失败保留目标镜像配置和错误结果，不自动回退已经可能迁移过数据的新应用。
恢复应结合任务的 `previousImage` 与站点备份操作，不能把镜像回退当成数据库回滚。

## 包与验证

updater 使用自己的 POM 版本和源码提交，不与博客应用版本绑定。构建脚本使用 Bash，
依赖 `jq`、`curl`、`zip`、`unzip`、`sha256sum` 等命令。发布另需 `gh`、AWS CLI v2
（支持 S3 条件写入）、Syft 和 Cosign。

`bash scripts/build-package.sh dist <updater版本> preview` 构建 Native、运行 HTTP 与重启
冒烟并生成独立 ZIP 和 `last.updater.Linux-{amd64,arm64}.version.json`。
ZIP 只包含可执行文件、本文和 Compose 附加配置。

工作流的 `publish_packages=true` 将两个架构的同一批 ZIP 及 SHA-256、SBOM、provenance、
Sigstore 签名发布到本仓库 GitHub Release 和 `https://dl.zrlog.com/updater/{channel}/`。
只有匿名下载并验证两处字节一致后，才写入 `zipSources` 并条件更新 updater 安装包清单。
构建阶段的 `zipSources` 为空，不能当作已经发布。
`last*.version.json` 更新清单只发布到 dl，不上传到 GitHub Release；GitHub 仅分发安装包
与校验附件。CI artifact 中的清单用于任务间传递构建身份及排查，运行时不会读取它。

若上传后公开下载暂时失败，可运行 `Recover preview package publication`，传入原构建
`run_id` 和信任 artifact 的 `artifact_attempt`。它复用原 ZIP、签名和 SBOM，核对构建
身份后继续分发，避免重新签名导致不可变附件冲突。源提交须匹配原工作流提交。

组织或仓库 Actions Secrets 需授权本仓库使用：`DOCKER_PASSWORD`、`ALIYUN_DOCKER_PASSWORD`、
`SECRET_ID`、`SECRET_KEY`、`BUCKET`、`HOST`。`HOST` 为 HTTPS S3 兼容端点。
工作流先检查所选分发渠道所需的密钥是否可读，再启动构建。

预览发布使用唯一 `v<preview版本>` tag 和 GitHub prerelease；正式 `channel=release`
只接受已经存在且匹配 POM 基础版本的 `vX.Y.Z` tag。本仓库维护者单独批准正式发版，
应用 Release Order 不再打包或发布 updater。此任务只发布预览，不创建正式 tag。

该安装包目录与 updater 读取的**应用升级清单**不同：运行时依然从 `build.properties`
配置的 `mirrorWebSite` 读取 `{release,preview}/last.docker-image.version.json`。
应用镜像仓库选择继续由后台设置管理。

```bash
bash scripts/test-package.sh
shellcheck scripts/*.sh
# 仅在专用测试 Docker Engine 执行；测试创建并清理隔离项目、镜像和注册表。
ZRLOG_UPDATER_REAL_DOCKER=1 ./mvnw test
```

`.github/workflows/build-publish.yml` 在两个原生架构上验证 JVM、Native、ZIP 和真实 Docker 执行边界。
其中 Docker 测试使用最小镜像夹具；完整站点仍需完成安装、数据库和附件保留的升级验收。

## 仓库边界

本仓库维护独立 Docker 升级服务端和它自己的构建、安装包、镜像。
主工程维护博客应用的 Docker 镜像、应用清单和 Compose 集成；base 保留 HTTP 客户端、
版本检查，以及原有 ZIP/Native/WAR 升级路径；admin 提供设置和进度页面。
updater 不引入页面、数据库、安装器或主题模块。
