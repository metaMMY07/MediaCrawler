# OpenScope 项目迁移交接（给 Astra）

更新时间：2026-09-15（Asia/Taipei）

## 当前工作目录

项目已完整迁移到：`D:\OpenScope`

Git 远端：`https://github.com/metaMMY07/MediaCrawler.git`

当前分支：`master`，远端已同步。公开 Release：`v0.1.1`。

## 迁移核验

- C 盘原目录：`C:\Users\30622\Documents\ChatGPT\聚合搜索`。项目文件已全部不在 C 盘；旧根目录本身目前为空，但被桌面进程占用，系统未允许移除最后这个空目录。
- D 盘目标包含 `.git`、`app`、`docs`、`gradle`、`scripts`、Gradle wrapper、测试源码、交接文档、构建日志和 APK 交付记录。
- D 盘文件清单统计：12,538 个文件，1,769,571,120 bytes（包含本地构建缓存和诊断记录）。
- `git status`：干净；`git fsck --full` 未发现损坏对象，仅报告迁移前重建标签留下的 dangling tag/tree，不影响当前历史。
- APK `D:\OpenScope\artifacts\OpenScope-0.3.0-arm64-v8a.apk`：10,434,637 bytes；SHA-256 `0BF417A24FC8191988CECB3A0CE7822823CC70A090190D8653B82F054F352FAB`；apksigner 验证通过，证书 SHA-256 `583079a20081a1bedaf8c1fbdf93e92bf3cc3544e0f7246fef7b1625d9066a02`。
- C 盘原目录下的 `.git`、`.reference`、`.remote-inspect` 已逐路径移除；剩余只有空的原目录，待占用它的进程退出后即可删除。

## 已发布内容

- 仓库：[metaMMY07/MediaCrawler](https://github.com/metaMMY07/MediaCrawler)
- Release：[OpenScope Android v0.1.1](https://github.com/metaMMY07/MediaCrawler/releases/tag/v0.1.1)
- 贡献者：`metaMMY07`、`KellenGO`（README 与发布提交的 Co-authored-by 均已记录）。
- Release 资产为 ARM64 APK；源码内 versionName 仍是 `0.3.0`，GitHub 发布标签按需求为 `v0.1.1`。

## 继续开发前

```powershell
Set-Location D:\OpenScope
pwsh -File .\scripts\build-local.ps1 -Target Verify
```

不要回到 C 盘旧路径；不要执行 `clean` 或递归删除命令。真实账号登录后的小红书卡片分页、目标真机性能和蜂窝网络稳定性仍需验证，详见 `docs/release-0.3.0.md`。
