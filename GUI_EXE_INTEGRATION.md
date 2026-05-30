# MGUI WebView2 GUI.exe 集成说明

## 📋 概述

MGUI 模组现已改造为使用 `webviewclib/GUI.exe` 来加载 HTML UI，通过本地 HTTP 服务器（端口 26698）提供资源服务。

## ✅ 核心特性

- ✅ **独立浏览器窗口** - 使用 GUI.exe 启动独立的 WebView2 浏览器窗口
- ✅ **本地 HTTP 服务器** - 端口 26698，提供 HTML/CSS/JS 等资源
- ✅ **端口占用检测** - 启动时检测端口是否被占用，防止多实例冲突
- ✅ **ZIP 资源包缓存** - 自动下载并解压 ZIP 资源包到本地缓存
- ✅ **完整 Web 支持** - 支持 HTML5/CSS3/JavaScript/MDUI 等现代技术

## 🔧 架构说明

### 组件结构

```
┌─────────────────────────────────────────┐
│         Minecraft 客户端                 │
├─────────────────────────────────────────┤
│                                         │
│  MGuiClientMod                          │
│    ├─ 初始化 WebView 管理器              │
│    ├─ 注册网络监听器                     │
│    └─ 管理生命周期                       │
│                                         │
│  MGuiWebViewManager                     │
│    ├─ 检测端口占用 (26698)              │
│    ├─ 启动本地 HTTP 服务器               │
│    └─ 调用 GUI.exe 打开浏览器            │
│                                         │
│  MGuiLocalHttpServer                    │
│    ├─ /ui/{uiId}/{file} - 资源服务      │
│    ├─ /api/* - API 接口                 │
│    └─ CORS 支持                         │
│                                         │
│  MGuiResourcePackManager                │
│    ├─ 接收 ZIP 数据块                    │
│    ├─ 解压到缓存目录                     │
│    └─ 管理资源路径映射                   │
│                                         │
└──────────────┬──────────────────────────┘
               │
               │ 启动命令
               ▼
┌─────────────────────────────────────────┐
│      webviewclib/GUI.exe                │
│                                         │
│  gui.exe "file://path/to/lag.html"     │
│                                         │
│  ┌───────────────────────────────┐     │
│  │   WebView2 浏览器窗口          │     │
│  │                               │     │
│  │  加载 lag.html                │     │
│  │  从 http://127.0.0.1:26698   │     │
│  │       /ui/{uiId}/... 获取资源 │     │
│  └───────────────────────────────┘     │
└─────────────────────────────────────────┘
```

### 工作流程

#### 1. 游戏启动流程

```
1. MGuiClientMod.onInitializeClient()
   ↓
2. 初始化 MGuiWebViewManager
   ↓
3. 检测端口 26698 是否被占用
   ├─ 已占用 → 抛出异常，游戏无法启动
   └─ 未占用 → 继续
   ↓
4. 启动 MGuiLocalHttpServer (端口 26698)
   ↓
5. 验证 GUI.exe 是否存在
   ↓
6. 初始化完成，游戏正常启动
```

#### 2. UI 打开流程

```
1. 服务器发送 open_ui 消息
   ↓
2. 客户端接收并准备接收 ZIP 数据
   ↓
3. 服务器分块发送 ZIP 数据 (zip_chunk)
   ↓
4. 客户端接收所有数据块并合并
   ↓
5. 发送 zip_complete 标记
   ↓
6. 客户端解压 ZIP 到缓存目录
   ├─ mgui/resourcepacks/{uiId}/
   │   ├─ lag.html
   │   ├─ styles.css
   │   └─ script.js
   ↓
7. 创建 MGuiHtmlScreen
   ↓
8. MGuiWebViewManager.openUi()
   ├─ 构建 file:// URL
   └─ 启动 GUI.exe: gui.exe "file://.../lag.html"
   ↓
9. GUI.exe 打开独立浏览器窗口
   ↓
10. 浏览器从本地 HTTP 服务器加载资源
```

#### 3. 资源加载流程

```
浏览器请求: http://127.0.0.1:26698/ui/testui/styles.css
   ↓
MGuiLocalHttpServer.UiResourceHandler
   ↓
解析路径: uiId = "testui", filename = "styles.css"
   ↓
读取文件: mgui/resourcepacks/testui/styles.css
   ↓
返回文件内容 (Content-Type: text/css)
   ↓
浏览器渲染样式
```

## 🚀 使用方法

### 1. 准备环境

确保 `webviewclib` 目录包含以下文件：
```
webviewclib/
├── GUI.exe           # 必需
├── edgeview.dll      # WebView2 运行时
├── krnln.fnr
├── mp3.run
└── spec.fne
```

### 2. 注册 UI

将 ZIP 资源包放到 `run/mguiregs/` 目录：
```bash
/guireg lag-screen.zip testui "测试UI"
```

### 3. 打开 UI

```bash
/mgui_open @s gui:testui
```

### 4. 预期行为

- ✅ 游戏启动时检测端口 26698
- ✅ 如果端口被占用，显示错误并退出游戏
- ✅ 打开 UI 时启动 GUI.exe
- ✅ 弹出独立的浏览器窗口
- ✅ 浏览器加载 lag.html 及相关资源
- ✅ 按 ESC 关闭游戏内提示和浏览器窗口

## ⚙️ 配置说明

### 端口配置

默认端口：**26698**

如需修改，编辑 `MGuiWebViewManager.java`：
```java
private static final int DEFAULT_PORT = 26698; // 改为其他端口
```

### GUI.exe 路径

默认路径：`webviewclib/GUI.exe`

如需修改，编辑 `MGuiWebViewManager.java`：
```java
private static final String WEBVIEW_LIB_PATH = "webviewclib/";
private static final String GUI_EXE_PATH = WEBVIEW_LIB_PATH + "GUI.exe";
```

## 🔍 故障排查

### Q1: 游戏启动失败，提示"端口已被占用"

**原因：** 端口 26698 已被其他进程占用

**解决方法：**
1. 检查是否有另一个游戏实例正在运行
2. 查找占用端口的进程：
   ```powershell
   netstat -ano | findstr :26698
   taskkill /F /PID <PID>
   ```
3. 或修改默认端口号

### Q2: GUI.exe 未启动

**原因：** GUI.exe 文件不存在或路径错误

**解决方法：**
1. 确认 `webviewclib/GUI.exe` 存在
2. 检查日志中的警告信息
3. 确保工作目录正确

### Q3: 浏览器窗口空白

**原因：** 资源路径错误或 HTTP 服务器未启动

**解决方法：**
1. 检查日志中 HTTP 服务器是否成功启动
2. 确认 HTML 文件存在于缓存目录
3. 在浏览器中访问 `http://127.0.0.1:26698/ui/{uiId}/lag.html` 测试

### Q4: 资源加载失败

**原因：** 文件路径不正确或权限问题

**解决方法：**
1. 检查 `mgui/resourcepacks/{uiId}/` 目录是否存在
2. 确认文件权限可读
3. 查看 HTTP 服务器日志中的 404 错误

## 📝 开发建议

### HTML 资源引用

在 HTML 中使用相对路径引用资源：
```html
<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" href="styles.css">
    <script src="script.js"></script>
</head>
<body>
    <img src="images/logo.png">
</body>
</html>
```

### JS 与 Java 通信

目前通过本地 HTTP 服务器的 `/api/` 端点进行通信：

```javascript
// JavaScript 端
fetch('http://127.0.0.1:26698/api/command', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({command: '/say Hello'})
});
```

## 🔗 相关资源

- [WebView2 官方文档](https://docs.microsoft.com/microsoft-edge/webview2/)
- [Java HttpServer API](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpServer.html)
- [Minecraft Fabric API](https://fabricmc.net/wiki/documentation)

---

**最后更新**: 2026-05-23  
**版本**: MGUI 2.0.0 (GUI.exe 模式)
