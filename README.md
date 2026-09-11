# Chronota

一个用于计划和记录的 Android 时间管理应用。

- **Plan** 是原本打算做什么，**Record** 是实际做了什么，两者相互独立。
- 无账号、无云同步，数据保存在本机。

时间轴、计时与番茄钟、循环计划、目标、两级分类与自定义属性、回顾统计，以及本地文件与 WebDAV 备份。

当前版本 0.1.1（预览），支持 Android 8.0 及以上。

构建 `./gradlew.bat :app:assembleDebug`，发布 `./scripts/release.ps1`（APK 复制到桌面）。
需求见 [PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md)，开发约定见 [AGENTS.md](AGENTS.md)。
