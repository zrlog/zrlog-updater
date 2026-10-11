# ZrLog updater

本仓库维护独立 Docker Compose updater 服务、HTTP 协议实现、Native 程序、安装包与镜像发布。
ZrLog 主工程保留应用镜像与整站 Compose 集成；base 保留升级检查和 HTTP 客户端，admin 保留页面。

- Java 源码与测试使用标准 `src/main`、`src/test` 布局，POM 不继承应用父 POM。
- 运行时只引入必要的轻量依赖，不引入 admin、install、blog、数据库或主题模块。
- 元数据源和 Docker 清单名来自 `src/main/resources/build.properties`，通过 Maven 属性配置。
- 修改接口、状态结构和错误处理时核对 base 客户端与 admin 消费者；Native JSON DTO 同步反射注册。
- 保留已有项目名、卷、端口、网络、环境和 `.env` 内容；替换前验证整个 Compose 配置。
- 不允许任意镜像或 shell 参数进入 HTTP 接口；拉取失败保留原容器，失败状态不能伪装为成功。
- 版本及构建身份属于本仓库，不使用应用版本或应用 source commit 作为 updater 的身份。
- 构建、组包和分发脚本使用 Bash 与标准 CLI，不增加 Python 或 Node 脚本依赖。
- 本仓库当前维护 Docker 升级服务端；base 保留 HTTP 客户端及原有 ZIP/Native/WAR 升级路径。

验证入口：

```bash
./mvnw -B test package
bash scripts/test-package.sh
./mvnw -B -Pnative -DskipTests package
bash scripts/smoke-native.sh target/zrlog-updater
ZRLOG_UPDATER_REAL_DOCKER=1 ./mvnw -B test
```

真实 Docker 测试只针对隔离项目；执行前确认允许在测试引擎创建和清理临时容器、卷和镜像。
CI 在 AMD64、ARM64 原生 Runner 上执行全部测试、Native、ZIP 与容器验证。
push/PR 只构建验证；发布需显式选择工作流的对应开关。正式版本使用已经存在且匹配版本的
`vX.Y.Z` tag，镜像和安装包保持不可变。主工程的正式 Release Order 不负责本仓库发版。
GitHub/dl 字节校验通过后才发布 updater 自身的安装包清单，不能改写应用通道清单。
