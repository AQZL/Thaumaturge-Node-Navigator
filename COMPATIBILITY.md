# Thaumaturge 兼容性

2026-11-17 核对了上游分支与 CurseForge 实际发布 JAR。

| Minecraft | NeoForge 构建版本 | Java | Thaumaturge 编译版本 | 接受的 Thaumaturge 版本 |
| --- | --- | --- | --- | --- |
| 1.21.1 | 21.1.238 | 21 | 1.1.0 | 1.1.0–1.1.1 |
| 26.1.2 | 26.1.2.122 | 25 | 1.1.0 | 1.1.0 |

两个 Minecraft 版本最新正式发布版都是 Thaumaturge 1.1.0。1.21.1 分支的 1.1.1 尚未发布，相关扫描、研究、节点与要素接口已经过源码核对；实际编译和运行依赖使用已发布的 1.1.0。更高的 Thaumaturge 版本需要重新检查。

上游源码依据：

- [26.1.2 / main，99abfc7](https://github.com/Leclowndu93150/Thaumaturge/tree/99abfc7105eecf0655e46365adbe85741a1d78ad)
- [1.21.1，999486b](https://github.com/Leclowndu93150/Thaumaturge/tree/999486b1cff8e34094ed870157657e07adc5f743)

## 安装

客户端和服务器都安装对应 Minecraft 版本的节点导航 JAR，以及 Thaumaturge 和它要求的 Curios、Lithostitched。两个节点导航安装包的模组版本为 1.0.1，文件名带 Minecraft 版本：

- `build/libs/thaumaturge_node_navigator-1.0.1-mc1.21.1.jar`
- `build/libs/thaumaturge_node_navigator-1.0.1-mc26.1.2.jar`

更新时移除 mods 目录中旧的节点导航 JAR，再放入对应的新包。同一实例只安装一个节点导航 JAR。`-sources.jar` 为源码包。

## 构建

版本配置分别位于 `gradle/targets/1.21.1.properties`、`gradle/targets/26.1.2.properties`，默认构建 26.1.2。将配置中列出的真实依赖 JAR 放入 `.local/dependencies/`，也可以用 `-Pthaumaturge_jar=...`、`-Pcurios_jar=...`、`-Plithostitched_jar=...` 覆盖路径。

PowerShell：

```powershell
.\gradlew.bat build '-Pminecraft_target=1.21.1' --console=plain
.\gradlew.bat build '-Pminecraft_target=26.1.2' --console=plain
```

Gradle 自动选择目标所需的 Java 工具链。临时构建文件位于 `build/<Minecraft版本>/`，游戏开发目录位于 `run/<Minecraft版本>/`，两个目标分别保存，安装包共同输出到 `build/libs/`。

1.21.1 构建复用扫描、节点校验、缓存、筛选、导航和数据包代码；生成任务仅转换资源标识符等平台名称，并覆盖 `src/minecraft1211/java/` 中的界面、输入和数据包注册适配。修改共享逻辑后请构建两个目标。

## 验证

两个目标均已使用正式发布的 Thaumaturge 1.1.0 完成构建，并各通过现有的 285 项自动检查。检查涵盖数据包、缓存、导航、筛选、滚动、按钮及节点自动清理。安装包和源码包的模组依赖声明、Mixin Java 级别与安装包字节码版本已核验。

此前使用节点导航 1.0.0 的两个目标开发专服均已在独立测试存档中完成模组加载并到达 `Done` 就绪状态，确认没有模组加载或 Mixin 注入错误；测试后结束了服务器进程。测试存档分别位于 `run/1.21.1/world/` 和 `run/26.1.2/world/`。这是 1.0.0 的历史运行验证；1.0.1 仅更新版本元数据，本次重新构建和执行自动检查，未重复启动游戏。

完整的游戏内扫描、补录、HUD、联机和节点破坏测试仍需在游戏中确认。
