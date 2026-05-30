# WebView2 浏览器集成说明

## 📋 概述

MGUI 模组现已支持使用系统默认浏览器打开 HTML UI 界面。当玩家触发UI时，会自动在外部浏览器中打开 `lag.html` 文件。

## ✅ 当前实现

### 工作流程
1. 服务器发送 ZIP 资源包到客户端
2. 客户端解压到缓存目录
3. 打开 UI 时，自动使用系统默认浏览器打开 `lag.html`
4. 游戏内显示提示信息"浏览器窗口已打开"
5. 按 ESC 关闭 UI（但浏览器窗口保持打开）

### 优点
- ✅ **零依赖** - 无需安装额外的运行时
- ✅ **完整支持** - 完美支持 HTML5/CSS3/JavaScript/MDUI
- ✅ **跨平台** - Windows/Linux/Mac 均可使用
- ✅ **稳定可靠** - 使用成熟的 Desktop API

### 缺点
- ❌ 浏览器窗口独立于游戏窗口
- ❌ 无法实现游戏内嵌 UI
- ❌ JS-Java 双向通信受限

---

## 🔧 未来改进方向

### 方案 A: 真正的 WebView2 嵌入窗口（推荐用于生产环境）

要实现真正的无边框浏览器窗口嵌入，需要：

1. **使用 webview-java 库**
   ```gradle
   implementation 'io.github.webview-official:webview-java:0.1.0'
   ```

2. **创建原生窗口**
   - 使用 JNA 调用 Windows API 创建窗口
   - 嵌入 WebView2 控件
   - 实现无边框、无地址栏

3. **实现 JS 桥接**
   - 通过 WebView2 的 `addHostObjectToScript` API
   - 实现 Java ↔ JavaScript 双向通信

**工作量**: 约 2-3 天

### 方案 B: 使用 JCEF (Chromium Embedded Framework)

1. **添加 JCEF 依赖**
   ```gradle
   implementation 'org.bitbucket.l_jray.jcef:jcef:latest'
   ```

2. **初始化 CEF**
   - 加载 Chromium 引擎
   - 创建离屏渲染上下文

3. **渲染到 Minecraft**
   - 将 CEF 输出渲染为 OpenGL 纹理
   - 在 Minecraft GUI 中显示

**工作量**: 约 5-7 天
**缺点**: 体积增加 ~100MB

### 方案 C: 使用 JavaFX WebView

1. **添加 JavaFX 依赖**
   ```gradle
   implementation 'org.openjfx:javafx-web:21'
   ```

2. **创建 JavaFX 窗口**
   - 嵌入 WebView 组件
   - 加载本地 HTML

3. **与 Minecraft 集成**
   - 在单独的线程运行 JavaFX
   - 实现窗口管理

**工作量**: 约 1-2 天
**优点**: 相对轻量，易于实现

---

## 🚀 如何升级到真正的 WebView2 嵌入

### 步骤 1: 安装 WebView2 Runtime

用户需要安装 Microsoft Edge WebView2 Runtime：
- 下载地址: https://developer.microsoft.com/microsoft-edge/webview2/
- 大多数 Windows 10/11 已预装

### 步骤 2: 添加 webview-java 依赖

修改 `build.gradle`:
```gradle
dependencies {
    // 移除 JNA
    // implementation 'net.java.dev.jna:jna:5.14.0'
    
    // 添加 webview-java
    implementation 'io.github.webview-official:webview-java:0.1.0'
}
```

### 步骤 3: 重写 WebView2Browser 类

```java
import com.webview.WebView;

public class WebView2Browser {
    private WebView webView;
    
    public void show() {
        webView = WebView.create();
        webView.setTitle("MGUI - " + uiId);
        webView.setSize(width, height);
        webView.navigate(htmlPath);
        webView.run();
    }
}
```

### 步骤 4: 实现 JS 桥接

```java
// Java 端
webView.addHostObjectToScript("mgui", jsBridge);

// JavaScript 端
window.mgui.close();
window.mgui.executeCommand("/say hello");
```

---

## 📝 当前使用说明

### 测试 UI

1. **注册 UI**
   ```bash
   /guireg lag-screen.zip testui "测试UI"
   ```

2. **打开 UI**
   ```bash
   /mgui_open @s gui:testui
   ```

3. **预期行为**
   - 游戏内显示半透明背景和提示文字
   - 系统默认浏览器自动打开，显示完整的 HTML UI
   - 可以在浏览器中与 UI 交互
   - 按 ESC 关闭游戏内的 UI 提示

### 注意事项

⚠️ **浏览器窗口不会自动关闭**
- 按 ESC 只会关闭游戏内的提示
- 需要手动关闭浏览器窗口

⚠️ **JS 接口不可用**
- 由于是外部浏览器，`mgui.close()` 等 JS 接口无法工作
- 需要真正的 WebView2 嵌入才能实现

---

## 💡 建议

对于**开发测试阶段**，当前的系统浏览器方案已经足够：
- 可以验证 HTML/CSS/JS 是否正确
- 可以测试 MDUI 等框架
- 可以快速迭代 UI 设计

对于**生产环境**，建议实现真正的 WebView2 嵌入：
- 提供更好的用户体验
- 实现完整的 JS-Java 通信
- UI 与游戏无缝集成

---

## 🔗 相关资源

- [WebView2 官方文档](https://docs.microsoft.com/microsoft-edge/webview2/)
- [webview-java GitHub](https://github.com/webview/webview_java)
- [JCEF 项目](https://bitbucket.org/chromiumembedded/java-cef)
- [JavaFX WebView](https://openjfx.io/)
