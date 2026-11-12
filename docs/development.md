# 构建与测试

## 环境

JDK 17、Android SDK 34；最低运行版本为 API 26。Gradle Wrapper 随仓库提供，依赖版本由构建文件指定。

```sh
git clone https://github.com/yangyao/navidrome-tv.git
cd navidrome-tv
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

使用 `ANDROID_HOME` 或本地 `local.properties` 指定 Android SDK。`local.properties`、IDE 设置、Gradle 缓存、APK、测试证据和签名密钥均不提交。

## APK

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

输出位于 `app/build/outputs/apk/`。当前 Release 已启用 R8 和资源缩减，但使用本机 Android debug key 签名，适合自行侧载；不同电脑的 debug key 不同，不能保证互相覆盖安装。正式分发前应配置自己的稳定签名，密钥保存在仓库外。

若文件系统监听导致本机构建停住，可关闭监听与原生文件系统集成：

```sh
./gradlew --no-daemon --no-watch-fs -Dorg.gradle.native=false \
  :app:assembleDebug :app:testDebugUnitTest
```

## 单元测试

```sh
./gradlew :app:testDebugUnitTest
```

涵盖认证 token、队列来源／快照、播放器窗口边界、预载策略、缓存容量与期限，以及分页去重。

## 设备整合测试

使用专用 Android TV 模拟器，先通过 `adb devices` 确认目标：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s <模拟器序列号> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <模拟器序列号> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <模拟器序列号> shell am instrument -w \
  com.yangyao.navidrometv.test/androidx.test.runner.AndroidJUnitRunner
```

测试运行真实 ExoPlayer，并在程序内启动 HTTP 测试服务器，提供合成歌曲 metadata、有效 WAV 音频和封面。测试暂存设备上的原设置，结束时恢复；不依赖私人 Navidrome 或真实音乐文件。

测试期间不要操作同一模拟器。如果测试进程被强制结束，应检查原连接设置是否已恢复；推荐使用专用测试模拟器。

## 结构

| 目录 | 内容 |
| --- | --- |
| `app/src/main/java/com/navidrome/tv/ui/` | Compose 页面、焦点、导航与界面状态 |
| `app/src/main/java/com/navidrome/tv/playback/` | Media3 播放服务、队列窗口和播放模式 |
| `app/src/main/java/com/navidrome/tv/data/` | Subsonic API、设置与数据缓存 |
| `app/src/main/java/com/navidrome/tv/pairing/` | 电视配对服务、二维码与手机设置页 |
| `app/src/test/` | JVM 单元测试 |
| `app/src/androidTest/` | 设备整合测试与本机服务器 |

播放器入口统一派发至 main looper；网络、认证与播放项准备在 IO 执行。高频播放进度与低频队列状态分开，只有进度组件订阅时钟。远端分页与本地播放器窗口独立，播放器最多保留 300 个播放项。
