# ZrLog updater

本仓库维护应用升级 SPI、升级实现，以及独立 Docker Compose updater 服务和其发布。
公共升级 DTO、清单解析、下载校验以及 ZIP/Native/WAR/FaaS/Docker/deb 实现均在本仓库。
主工程负责装配，base 提供宿主适配，admin 保留页面和标准 SSE。

- 各模块使用标准 `src/main`、`src/test` 布局，POM 不继承应用父 POM。
- `updater-spi` 是纯 Java 11 契约，不依赖 base；保留迁入 DTO 的包名与 JSON 字段。
- `updater-core` 经 ServiceLoader 注册实现，消费者只编译依赖 SPI，宿主装配运行时实现。
- `updater-docker-service` 保留 `zrlog-docker-updater` 坐标及根 `target/zrlog-updater` 路径。
- 运行时只引入必要的轻量依赖，不引入 admin、install、blog、数据库或主题模块。
- Docker 服务的元数据源和清单名来自 `updater-docker-service/src/main/resources/build.properties`。
- 修改接口、状态结构和错误处理时核对 base 客户端与 admin 消费者；Native JSON DTO 同步反射注册。
- 保留已有项目名、卷、端口、网络、环境和 `.env` 内容；替换前验证整个 Compose 配置。
- 不允许任意镜像或 shell 参数进入 HTTP 接口；拉取失败保留原容器，失败状态不能伪装为成功。
- 版本及构建身份属于本仓库，不使用应用版本或应用 source commit 作为 updater 的身份。
- 迁入资产的初始 Maven 版本为 `4.0.0-SNAPSHOT`，后续独立维护；不从 1.0.0 重新编号。
- 各 Provider 的中英文文案分别维护于 `i18n/updater/<类型>/backend_*.properties`，共享文案在 common。
- Provider 声明自己的语言资源，宿主通过 SPI 合并；不增加统一的大 properties 文件。
- deb 应用只写固定通道请求，root CLI 校验清单、SHA-256、包名/版本/架构后运行 dpkg。
- 构建、组包和分发脚本使用 Bash 与标准 CLI，不增加 Python 或 Node 脚本依赖。
- HTTP 请求由现有宿主适配，保持 ZrLog UA；不借迁移增加请求实现或响应大小限制。

验证入口：

```bash
./mvnw -B test package
bash scripts/test-application-modules.sh
bash scripts/test-package.sh
bash scripts/test-github-release.sh
./mvnw -B -Pnative -DskipTests package
bash scripts/smoke-native.sh target/zrlog-updater
ZRLOG_UPDATER_REAL_DOCKER=1 ./mvnw -B test
```

真实 Docker 测试只针对隔离项目；执行前确认允许在测试引擎创建和清理临时容器、卷和镜像。
CI 在 AMD64、ARM64 原生 Runner 上执行全部测试、Native、ZIP 与容器验证。
应用构件可独立用 `./mvnw -B -pl updater-spi,updater-core -am verify` 构建，
无需 Docker 环境；Native SPI/JSON 冒烟见 `scripts/test-application-modules.sh ... --native`。
`scripts/test-maven-consumer.sh` 验证临时仓库和空缓存消费；应用构件快照由
`Application updater modules` 工作流的显式 `publish_snapshots` 开关发布。
push/PR 只构建验证；发布需显式选择工作流的对应开关。正式版本使用已经存在且匹配版本的
`vX.Y.Z` tag，镜像和安装包保持不可变。主工程的正式 Release Order 不负责本仓库发版。
GitHub/dl 字节校验通过后才发布 updater 自身的安装包清单，不能改写应用通道清单。
