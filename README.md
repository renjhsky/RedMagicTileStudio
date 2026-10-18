# 红魔磁贴布局 · RedMagicTileStudio

为 NX789J 的原生控制中心调整圆形磁贴列数、大小、间距与文字。圆形磁贴左右可见边缘对齐上方卡片，顶部音乐、亮度、音量与宽卡片保持系统布局。

当前显示版本 **1.0**，内部安装版本 **5**。

## 支持范围

| 项目 | 范围 |
| --- | --- |
| 机型 | REDMAGIC NX789J |
| 系统 | REDMAGIC OS 11 / Android 16（SDK 36） |
| 框架 | 支持 libxposed API 102 的 Vector |
| 作用域 | 系统界面 `com.android.systemui` |

特定固件的布局已收到用户真机生效反馈。新增诊断恢复与更新入口仍需设备复核；其他机型或固件未确认兼容。

## 安装与更新

1. 从本仓库 **Releases** 下载 APK 并安装。
2. 在 Vector 启用“红魔磁贴布局”，作用域勾选“系统界面 / SystemUI”。
3. 重启手机，打开模块并启用优化。
4. 完全展开控制中心，再返回模块点击“检测实际生效状态”。

软件底部的“版本与更新 → 查看更新（GitHub）”打开本仓库发布页，可查看说明并下载新版。它通过浏览器访问，不在后台检查、下载或安装。无浏览器时可复制链接。

当前测试安装包沿用此前测试签名，以便覆盖现有安装。正式发行需使用单独保管的私钥；首次切换签名时不能直接覆盖测试版。详见 [发布说明](PUBLISHING.txt)。

## 设置与恢复

- 竖屏 3–7 列、横屏 3–8 列。
- 圆形磁贴视觉尺寸、横纵间距、文字大小与名称显示。
- “恢复推荐参数”保留启用状态；“一键恢复原厂布局”关闭优化。
- 当前故障和已恢复历史分别记录，避免配置读取恢复后仍显示旧报错。

无法进入设置时，在 Vector 停用模块后重启。

## 构建

版本统一位于 `version.properties`；GitHub 更新目标位于 `repository.properties`。在仓库创建成功后写入真实的 `owner/repository`：

```bash
python3 tools/configure-github.py YOUR_ACCOUNT/RedMagicTileStudio
```

配置外部 Android SDK 36、Java 和 Gradle 后可执行 `gradle :app:assembleDebug`。Termux 用户可使用 `bash build-termux.sh`。

独立构建使用现有 Android SDK 工具和 Eclipse ECJ，不自动下载 SDK 或接受许可：

```bash
python3 tools/build-standalone.py \
  --android-jar /path/to/android.jar \
  --build-tools /path/to/build-tools \
  --ecj /path/to/ecj.jar \
  --output /path/to/RedMagicTileStudio-NX789J-v1.0.apk
```

正式构建增加 `--release`，并按 [PUBLISHING.txt](PUBLISHING.txt) 配置外部签名环境变量。未配置真实仓库、缺少私钥或使用公开测试密钥时会拒绝正式构建。

公开工程与发布源码包不包含签名密钥。Gradle 默认使用 Android 本地调试签名；独立构建首次运行时在被 Git 忽略的 `.local-sign/debug.jks` 生成本地测试密钥。自行编译的调试包通常不能覆盖发布页的测试安装包。

需要沿用自己的测试签名时，设置 `RMT_KEYSTORE`、`RMT_KEY_ALIAS` 和 `RMT_SIGN_PASS`；已有的本地旧测试密钥也会保留。正式发行仍需使用单独保管的外部私钥。请勿将签名密钥、密码或 GitHub 访问令牌提交到仓库。

源码不包含原厂 SystemUI APK、Android SDK 或个人设备标识。`validation` 中保留历史构建检查记录，具体适用版本见报告。第三方编译依赖见 [THIRD-PARTY.md](THIRD-PARTY.md)。
