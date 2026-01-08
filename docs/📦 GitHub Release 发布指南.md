# 📦 GitHub Release 发布指南

发布 Release 可以让用户无需编译源码，直接下载你打包好的 APK 文件进行安装。

## 第一步：在 Android Studio 中生成 APK

发布之前，你需要先获取应用的安装包文件。

1. **生成调试版 APK (简单快速)**：
   - 点击顶部菜单栏 **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**。
   - 等待编译完成后，点击右下角弹窗中的 **locate**，找到 `app-debug.apk`。
2. **生成正式版 APK (推荐用于发布)**：
   - 点击 **Build** -> **Generate Signed Bundle / APK...**。
   - 选择 **APK**，点击 **Next**。
   - 创建或选择你的签名文件 (`.jks`)。
   - 选择 **release** 变体，点击 **Create**。
   - 生成的文件夹中会有一个 `app-release.apk`。

*注：建议将 APK 文件重命名为更有辨识度的名称，例如 `QingMeiDays_v1.0.0.apk`。*

## 第二步：使用 Git 打标签 (Tag)

Release 通常是基于 Git 的“标签”生成的。在终端执行：

```
# 1. 为当前提交打上版本标签
git tag -a v1.0.0 -m "第一个正式发布版本"

# 2. 将标签推送到远程仓库
git push origin v1.0.0
```

## 第三步：在 GitHub 页面创建 Release

1. 打开你的 GitHub 项目主页 `https://github.com/htllty/QingMeiDays`。

2. 在右侧边栏找到 **Releases** 栏目，点击 **Create a new release** (或者 **Draft a new release**)。

3. **Choose a tag**：选择你刚才推送的 `v1.0.0`。

4. **Release title**：输入标题，例如 `v1.0.0 - 初始版本上线`。

5. **Description**：使用 Markdown 编写更新说明：

   ```
   ## 🌸 新功能
   - 集成 Jetpack Glance 桌面小组件。
   - 支持倒数日/纪念日双模式切换。
   - 实现跨进程数据同步与零点自动刷新。
   
   ## 🔧 优化
   - 采用卡片式类型选择交互。
   - 完善了 DataManager 生命周期过滤逻辑。
   ```

6. **Attach binaries**：将第一步生成的 **APK 文件** 拖拽到下方的框中进行上传。

7. 点击 **Publish release**。

## 💡 专业贴士

- **Pre-release**：如果你的版本还在测试阶段，可以勾选 `Set as a pre-release`，它会标记为“预览版”。
- **Changelog**：GitHub 有一个 `Generate release notes` 按钮，点击它可以根据你的 Commit 历史自动生成更新日志。
- **版本规范**：建议遵循语义化版本 (SemVer)，即 `主版本号.次版本号.修订号` (如 `1.0.0`)。

**发布完成后，你的 README 页面右侧会自动显示最新的版本信息，用户点击即可下载 APK！**