# 每日健身 Daily Fitness

一个简洁的 Android 每日健身计划与重量记录 App，粉色主题，使用 Jetpack Compose 构建。

## 功能

- **今日训练**：顶部日期条默认把今天放在正中间，可左右滑动或点击查看任意日期的训练计划，并显示完成进度
- **滑动完成**：项目左滑标记完成（整行删除线），右滑撤销完成
- **按周模板**：按周一到周日设置训练项目，设置一次后以后每周的该星期都自动套用这套计划
- **自定义项目**：自由添加项目名称、组数、次数 / 时长（支持次数范围如 `4×6～10`、分钟、步数、纯组数等写法）
- **重量记录**：为硬拉、卧推等项目记录训练重量，调整重量时自动记录时间；折线图展示重量增长过程，支持手动补记与删除单条记录
- **纯本地存储**：SharedPreferences + JSON，无需联网，无账号

## 技术栈

- Kotlin、Jetpack Compose、Material 3
- 单 Activity 架构，数据层为 SharedPreferences 序列化仓库
- minSdk 24 / targetSdk 37

## 构建

用 Android Studio 打开本目录，Gradle 同步后直接 Run；或使用命令行：

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

构建产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 下载

可直接在 [Releases](../../releases) 页面下载预编译 APK。

## License

[MIT](LICENSE)
