# WebView2 独立浏览器窗口使用指南

## 📋 概述

MGUI 模组现已集成 **webview-java**，可以创建真正的独立浏览器窗口来显示 HTML UI。

## ✅ 功能特性

- ✅ **独立窗口** - 有边框、可调整大小的纯浏览器窗口
- ✅ **完整支持** - HTML5/CSS3/JavaScript/MDUI 等现代 Web 技术
- ✅ **JS 桥接** - 支持 JavaScript 与 Java 的双向通信
- ✅ **跨平台** - Windows/macOS/Linux（需要相应的 WebView2 Runtime）
- ✅ **轻量级** - 无需打包 Chromium，使用系统自带的 WebView2

## 🔧 系统要求

### Windows
- Windows 10/11
- Microsoft Edge WebView2 Runtime（通常已预装）
- 如果未安装，下载地址：https://developer.microsoft.com/microsoft-edge/webview2/

### macOS
- macOS 10.14+
- 使用系统自带的 WebKit

### Linux
- GTK3 + WebKitGTK
- 安装命令：`sudo apt-get install libwebkit2gtk-4.0-dev`

## 🚀 使用方法

### 1. 注册 UI

将 ZIP 资源包放到 `run/mguiregs/` 目录，然后执行：

```bash
/guireg lag-screen.zip testui "测试UI"
```

### 2. 打开 UI

```bash
/mgui_open @s gui:testui
```

### 3. 预期行为

- ✅ 弹出一个独立的浏览器窗口
- ✅ 窗口标题显示 "MGUI - testui"
- ✅ 窗口中加载并显示 `lag.html`
- ✅ 可以在浏览器中与 UI 交互（点击按钮、输入表单等）
- ✅ 游戏内显示半透明背景和提示信息
- ✅ 按 ESC 关闭游戏内提示和浏览器窗口

## 💻 JS 接口

在 HTML 中可以使用以下 JavaScript 接口：

```javascript
// 关闭 UI
window.mgui.close();

// 执行 Minecraft 命令
window.mgui.executeCommand("/say Hello from WebView!");

// 获取截图（返回 URL）
const screenshotUrl = window.mgui.getScreenshot();

// 获取玩家名称
const playerName = window.mgui.getPlayerName();

// 获取 UI ID
const uiId = window.mgui.getUiId();

// 发送消息到聊天栏
window.mgui.sendMessage("Hello!");

// 通知服务器 UI 已关闭
window.mgui.notifyClosed();
```

### 示例 HTML

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>MGUI Test</title>
    <link rel="stylesheet" href="https://unpkg.com/mdui@2/mdui.css">
    <script src="https://unpkg.com/mdui@2/mdui.global.js"></script>
</head>
<body>
    <mdui-button onclick="window.mgui.close()">
        关闭 UI
    </mdui-button>
    
    <mdui-button onclick="window.mgui.executeCommand('/give @s diamond')">
        获得钻石
    </mdui-button>
    
    <p>玩家: <span id="player"></span></p>
    
    <script>
        // 显示玩家名称
        document.getElementById('player').textContent = window.mgui.getPlayerName();
    </script>
</body>
</html>
```

## ⚙️ 技术细节

### 窗口配置

当前默认配置：
- **宽度**: 800px（跟随游戏窗口）
- **高度**: 600px（跟随游戏窗口）
- **可调整大小**: 是
- **标题**: "MGUI - {uiId}"

### 线程模型

WebView 运行在**独立的后台线程**中，不会阻塞 Minecraft 主线程：

```
Minecraft 主线程          WebView 线程
     |                        |
     |--- 创建 WebView ------>|
     |                        |-- 加载 HTML
     |                        |-- 运行事件循环
     |<-- 初始化完成 ---------|
     |                        |
     |--- 关闭请求 ---------->|
     |                        |-- 清理资源
     |                        |-- 退出
```

### JS 桥接实现

通过 `webView.evaluate()` 方法注入 JavaScript 对象：

```java
String jsCode = "window.mgui = { close: function() {...}, ... };";
webView.evaluate(jsCode, result -> {
    LOGGER.debug("JS 桥接注入完成");
});
```

## 🐛 常见问题

### Q1: 窗口没有弹出？

**检查项：**
1. 确认 WebView2 Runtime 已安装
2. 查看日志是否有错误信息
3. 确认 HTML 文件存在且路径正确

**Windows 安装 WebView2：**
```powershell
# 以管理员身份运行 PowerShell
winget install Microsoft.EdgeWebView2Runtime
```

### Q2: JS 接口不可用？

**原因：** JS 桥接可能还未注入完成

**解决：** 在 HTML 中添加延迟检查：

```javascript
setTimeout(() => {
    if (window.mgui) {
        console.log("JS 桥接可用");
    } else {
        console.error("JS 桥接未加载");
    }
}, 1000);
```

### Q3: 窗口关闭后进程还在？

**原因：** WebView 线程可能未完全退出

**解决：** 确保调用 `webView.terminate()`，已在代码中实现。

### Q4: 中文显示乱码？

**解决：** 确保 HTML 文件使用 UTF-8 编码：

```html
<meta charset="UTF-8">
```

## 🔮 未来改进

### 计划功能

- [ ] 自定义窗口尺寸
- [ ] 无边框模式
- [ ] 窗口置顶选项
- [ ] 更完善的 JS-Java 双向通信
- [ ] 支持从 Java 调用 JS 函数
- [ ] 截图功能集成
- [ ] 资源缓存优化

### 性能优化

- [ ] 复用 WebView 实例（减少启动时间）
- [ ] 预加载常用资源
- [ ] 离屏渲染支持

## 📝 开发建议

### 调试技巧

1. **启用开发者工具**（如果 webview-java 支持）
2. **使用 console.log** 输出调试信息
3. **查看 Minecraft 日志** 了解 Java 端状态

### 最佳实践

1. **异步操作** - JS 调用 Java 可能是异步的，使用 Promise 或回调
2. **错误处理** - 始终检查 `window.mgui` 是否存在
3. **资源清理** - 关闭 UI 时释放所有资源
4. **响应式设计** - 窗口可调整大小，使用 CSS 媒体查询

## 🔗 相关资源

- [webview-java GitHub](https://github.com/webview/webview_java)
- [WebView2 官方文档](https://docs.microsoft.com/microsoft-edge/webview2/)
- [MDUI 组件库](https://www.mdui.org/)
- [Minecraft Fabric API](https://fabricmc.net/wiki/documentation)

---

**最后更新**: 2026-05-23
**版本**: MGUI 1.0.0
