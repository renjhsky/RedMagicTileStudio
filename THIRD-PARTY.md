# 第三方编译依赖

工程保留了原项目本地 Maven 目录中的以下编译依赖及 POM，便于复现既有构建：

| 组件 | 版本 | 用途 |
| --- | --- | --- |
| `io.github.libxposed:api` | 102.0.0 | 模块 API，`compileOnly` |
| `io.github.libxposed:interface` | 102.0.0 | 独立编译使用的接口 |
| `io.github.libxposed:annotation` | 1.0.0 | 本地接口编译依赖 |
| `io.github.libxposed:service` | 102.0.0 | 原项目本地 Maven 目录保留的组件，当前 APK 不使用 |

这些组件来自 libxposed，不属于本项目的原创代码。API 的原始 POM 标明 Apache License 2.0；对应许可证副本见 `third-party/Apache-2.0.txt`。其他组件保留其发布文件及元数据，具体许可按其上游条款执行。

上游地址：<https://github.com/libxposed/api>、<https://github.com/libxposed/service>。

独立构建时 Android SDK 和 Eclipse ECJ 由运行者单独提供，不包含在源码包中。上述 libxposed API 与接口不打包进交付 APK。
