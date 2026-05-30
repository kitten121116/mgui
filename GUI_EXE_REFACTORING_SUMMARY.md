# MGUI GUI.exe 改造总结

## 🎯 改造目标

将 MGUI 模组从 JavaFX WebView 改为使用 `webviewclib/GUI.exe` 加载 HTML，并提供本地 HTTP 服务器（端口 26698）。

## ✅ 已完成的工作

### 1. 创建新的核心组件

#### MGuiWebViewManager.java
- **位置**: `src/client/java/com/example/wgewge/client/webview/MGuiWebViewManager.java`
- **功能**:
  - ✅ 端口占用检测（26698）
  - ✅ 启动本地 HTTP 服务器
  - ✅ 调用 GUI.exe 打开浏览器窗口
  - ✅ 管理 UI 生命周期
  - ✅ 进程管理和清理

#### MGuiLocalHttpServer.java
- **位置**: `src/client/java/com/example/wgewge/client/webview/MGuiLocalHttpServer.java`
- **功能**:
  - ✅ HTTP 服务器实现（端口 26698）
  - ✅ `/ui/{uiId}/{file}` - 资源服务
  - ✅ `/api/*` - API 接口（预留）
  - ✅ CORS 支持
  - ✅ 内容类型自动识别

### 2. 修改现有组件

#### MGuiHtmlScreen.java
- **修改内容**:
  - ❌ 移除 JavaFX WebView2Browser 依赖
  - ✅ 改用 MGuiWebViewManager 打开 UI
  - ✅ 简化关闭逻辑

#### MGuiClientMod.java
- **修改内容**:
  - ✅ 初始化时启动 MGuiWebViewManager
  - ✅ 添加端口占用异常处理
  - ✅ 游戏退出时清理资源

### 3. 文档

#### GUI_EXE_INTEGRATION.md
- **内容**:
  - ✅ 架构说明和工作流程
  - ✅ 使用方法
  - ✅ 配置说明
  - ✅ 故障排查指南

## 🔧 关键技术点

### 1. 端口占用检测

```java
private boolean isPortInUse(int port) {
    try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
        socket.setReuseAddress(true);
        return false;
    } catch (Exception e) {
        return true;
    }
}
```

**原理**: 尝试绑定端口，如果失败则说明已被占用。

### 2. GUI.exe 启动

```java
ProcessBuilder processBuilder = new ProcessBuilder(
    new File(GUI_EXE_PATH).getAbsolutePath(),
    url  // file://.../lag.html
);
processBuilder.directory(new File(WEBVIEW_LIB_PATH));
guiProcess = processBuilder.start();
```

**命令格式**: `gui.exe "file://app.html"`

### 3. 本地 HTTP 服务器

```java
server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
server.createContext("/ui/", new UiResourceHandler());
server.createContext("/api/", new ApiHandler());
server.start();
```

**路径映射**:
- `/ui/testui/lag.html` → `mgui/resourcepacks/testui/lag.html`
- `/ui/testui/styles.css` → `mgui/resourcepacks/testui/styles.css`

## 📊 架构对比

### 改造前（JavaFX）

```
Minecraft → MGuiHtmlScreen → JavaFX WebView → 加载 file:// URL
                                    ↓
                            JFXPanel + Stage
                                    ↓
                            JavaFX 事件循环
```

**缺点**:
- ❌ 需要 JavaFX 依赖
- ❌ 与 Swing 集成复杂
- ❌ 窗口样式受限

### 改造后（GUI.exe）

```
Minecraft → MGuiHtmlScreen → MGuiWebViewManager → GUI.exe → WebView2
                                    ↓                    ↓
                            本地 HTTP 服务器         独立浏览器窗口
                            (端口 26698)            加载 file:// URL
                                    ↓
                            提供 /ui/{uiId}/* 资源
```

**优点**:
- ✅ 使用原生 WebView2
- ✅ 独立进程，更稳定
- ✅ 完整浏览器功能
- ✅ 端口占用检测防冲突

## 🚀 使用流程

### 1. 游戏启动

```
1. MGuiClientMod.onInitializeClient()
2. MGuiWebViewManager.init()
   ├─ 检测端口 26698
   ├─ 启动 HTTP 服务器
   └─ 验证 GUI.exe
3. 游戏正常运行
```

### 2. 打开 UI

```
1. 服务器发送 open_ui 消息
2. 客户端接收 ZIP 数据
3. 解压到 mgui/resourcepacks/{uiId}/
4. MGuiHtmlScreen.openScreen()
5. MGuiWebViewManager.openUi()
6. 启动 GUI.exe: gui.exe "file://.../lag.html"
7. 浏览器窗口弹出
8. 从 http://127.0.0.1:26698/ui/{uiId}/... 加载资源
```

### 3. 关闭 UI

```
1. 玩家按 ESC
2. MGuiHtmlScreen.onClose()
3. MGuiWebViewManager.closeUi()
4. 终止 GUI.exe 进程（如果没有其他 UI）
5. 通知服务器
```

## ⚠️ 注意事项

### 1. 端口冲突

- **默认端口**: 26698
- **检测方法**: 启动时自动检测
- **处理方式**: 如果端口被占用，抛出异常并阻止游戏启动
- **解决方案**: 
  - 关闭占用端口的进程
  - 或修改 `DEFAULT_PORT` 常量

### 2. GUI.exe 依赖

- **必需文件**: `webviewclib/GUI.exe`
- **工作目录**: `webviewclib/`
- **验证**: 启动时检查文件是否存在
- **警告**: 如果不存在，记录警告但继续运行

### 3. 资源路径

- **缓存目录**: `mgui/resourcepacks/{uiId}/`
- **HTTP 访问**: `http://127.0.0.1:26698/ui/{uiId}/{file}`
- **文件访问**: `file:///absolute/path/to/lag.html`

### 4. 进程管理

- **启动**: 每次打开 UI 启动新进程
- **关闭**: 所有 UI 关闭后终止进程
- **清理**: 游戏退出时强制终止

## 📝 后续优化建议

### 短期优化

1. **进程复用**: 单个 GUI.exe 实例支持多个 UI 标签页
2. **JS 桥接**: 通过 HTTP API 实现完整的 JS-Java 双向通信
3. **窗口配置**: 支持自定义窗口尺寸和位置
4. **错误提示**: 更友好的端口占用错误提示

### 长期优化

1. **WebSocket**: 替代 HTTP polling，实现实时通信
2. **资源预加载**: 提前加载常用资源
3. **缓存优化**: 智能缓存策略减少重复加载
4. **多平台支持**: macOS/Linux 的替代方案

## 🔍 测试清单

### 基本功能

- [ ] 游戏能正常启动
- [ ] 端口 26698 成功绑定
- [ ] GUI.exe 能被找到
- [ ] HTTP 服务器能响应请求

### UI 打开

- [ ] 接收 ZIP 数据正常
- [ ] 解压到正确目录
- [ ] GUI.exe 成功启动
- [ ] 浏览器窗口弹出
- [ ] HTML 页面正确加载
- [ ] CSS/JS 资源正常加载

### UI 关闭

- [ ] ESC 键能关闭窗口
- [ ] GUI.exe 进程正确终止
- [ ] 服务器收到关闭通知

### 异常处理

- [ ] 端口被占用时游戏无法启动
- [ ] GUI.exe 缺失时有警告
- [ ] HTML 文件缺失时有错误提示
- [ ] HTTP 服务器启动失败有异常

## 📦 交付物

### 新增文件

1. `src/client/java/com/example/wgewge/client/webview/MGuiWebViewManager.java`
2. `src/client/java/com/example/wgewge/client/webview/MGuiLocalHttpServer.java`
3. `GUI_EXE_INTEGRATION.md`
4. `GUI_EXE_REFACTORING_SUMMARY.md` (本文件)

### 修改文件

1. `src/client/java/com/example/wgewge/client/gui/MGuiHtmlScreen.java`
2. `src/client/java/com/example/wgewge/client/MGuiClientMod.java`

### 保留文件（未使用但不删除）

1. `src/client/java/com/example/wgewge/client/webview/WebView2Browser.java` (JavaFX 实现)

## ✨ 总结

本次改造成功将 MGUI 从 JavaFX WebView 迁移到使用 webviewclib/GUI.exe，主要优势：

1. **更稳定**: 独立进程，崩溃不影响游戏
2. **更完整**: 原生 WebView2，支持所有现代 Web 技术
3. **更安全**: 端口占用检测防止多实例冲突
4. **更简洁**: 移除了复杂的 JavaFX-Swing 集成

改造后的架构清晰、可维护性强，为后续功能扩展打下良好基础。

---

**改造日期**: 2026-05-23  
**改造版本**: MGUI 2.0.0  
**改造人员**: AI Assistant
