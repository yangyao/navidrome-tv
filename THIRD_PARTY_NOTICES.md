# 第三方说明

Navidrome TV 是独立实现的 Android TV 客户端。MIT 许可证适用于本仓库原创部分，不替代依赖组件或运行时媒体内容的许可证。

| 组件 | 用途 | 许可证与来源 |
| --- | --- | --- |
| AndroidX / Jetpack Compose / Media3 | 界面、生命周期和媒体播放 | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| Kotlin / kotlinx.coroutines / kotlinx.serialization | 语言、协程和 JSON | [Kotlin 项目](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)、[coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)、[serialization](https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt)，Apache-2.0 |
| OkHttp | 网络请求 | [Apache-2.0](https://github.com/square/okhttp/blob/master/LICENSE.txt) |
| Coil | 封面加载与缓存 | [Apache-2.0](https://github.com/coil-kt/coil/blob/2.6.0/LICENSE.txt) |
| ZXing | 二维码生成 | [Apache-2.0](https://github.com/zxing/zxing/blob/master/LICENSE) |
| Gradle Wrapper | 可重现的构建入口 | [Apache-2.0](https://github.com/gradle/gradle/blob/v8.9.0/LICENSE)；保留 Wrapper 脚本原有版权声明 |
| JUnit / AndroidX Test | 自动化测试 | [JUnit EPL-1.0](https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt)、[AndroidX Test Apache-2.0](https://github.com/android/android-test/blob/main/LICENSE) |

依赖由 Gradle 下载，精确版本见 `app/build.gradle.kts`、根构建文件和 Wrapper 配置。

设计参考：[CraftWorksMC/Chora](https://github.com/CraftWorksMC/Chora)。未将其代码、Git 历史或参考截图加入公开源码。

README 截图来自实际运行的应用，展示个人 Navidrome 服务器提供的曲名和封面；这些媒体的权利属于各自权利人，不随本项目 MIT 许可证转移。应用不内置这些音乐或封面。
