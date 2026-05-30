# MGUI - Minecraft GUI System

## 项目概述

MGUI是一个专注于**服务器+客户端**架构的Minecraft Fabric模组，通过消息通道发送HTML界面资源包，由客户端解析并渲染，实现精美的GUI界面。

### 核心特性

✅ **服务器-客户端分离架构**：独立的服务端和客户端模组类  
✅ **HTML/CSS/JS支持**：支持大多数HTML语法，包括CSS样式和JavaScript交互  
✅ **自定义JS接口**：提供游戏截图、执行客户端命令等特殊功能  
✅ **命令-UI映射**：服务器注册命令与UI资源的对应关系  
✅ **动态资源加载**：按需下载和管理HTML资源包  
✅ **强制UI打开**：服务器可以直接发送指令给客户端强制打开某个UI  

---

## 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                    业务 Mod                              │
│  (可选，使用 MGuiApi 注册 UI)                            │
└────────────────────┬────────────────────────────────────┘
                     │ 注册 UI 资源
                     ▼
┌─────────────────────────────────────────────────────────┐
│                  MGUI 服务器端                           │
│  - 管理命令与UI的映射关系                                │
│  - 接收业务mod的UI注册                                   │
│  - 向客户端发送打开UI指令                                │
│  - 提供 /mgui_open 和 /mgui_list 命令                   │
└────────────────────┬────────────────────────────────────┘
                     │ 消息通道 (Fabric Networking)
                     ▼
┌─────────────────────────────────────────────────────────┐
│                  MGUI 客户端                             │
│  - 接收服务器UI打开指令                                  │
│  - 下载HTML资源包                                        │
│  - 截取游戏画面作为背景                                  │
│  - 渲染HTML界面                                          │
│  - 提供JS桥接接口                                        │
└─────────────────────────────────────────────────────────┘
```

---

## 目录结构

```
src/
├── main/java/com/example/wgewge/
│   ├── Template.java                 # 主模组入口（服务端）
│   ├── MGuiConstants.java            # 常量定义
│   ├── server/
│   │   └── MGuiServerMod.java        # 服务器端核心类
│   └── api/
│       └── MGuiApi.java              # 业务mod使用的API
│
├── client/java/com/example/wgewge/client/
│   ├── TemplateClient.java           # 客户端入口
│   ├── MGuiClientMod.java            # 客户端核心类
│   ├── gui/
│   │   └── MGuiHtmlScreen.java       # HTML屏幕渲染
│   ├── js/
│   │   └── JsBridge.java             # JS桥接接口
│   ├── screenshot/
│   │   └── ScreenshotManager.java    # 截图管理器
│   ├── resource/
│   │   └── MGuiResourcePackManager.java  # 资源包管理器
│   └── network/
│       └── MGuiNetworkHandler.java   # 网络处理器
```

---

## 使用指南

### 1. 安装模组

将编译后的 `.jar` 文件放入：
- **服务器端**：`server/mods/` 目录
- **客户端**：`client/mods/` 目录

### 2. 业务Mod注册UI

在你的业务mod中，使用 `MGuiApi` 注册UI：

```java
// 在服务器端调用
MGuiApi.registerUi(
    player,                          // 玩家对象
    "mygui:open",                    // 触发命令
    "my_unique_ui_id",               // UI唯一标识
    "http://example.com/ui-pack.zip", // 资源包URL
    "我的自定义UI"                    // 描述
);
```

### 3. 服务器命令

#### `/mgui_open <player> <uiCommand>`
强制为指定玩家打开UI

```bash
/mgui_open Steve mygui:open
```

#### `/mgui_list`
列出所有已注册的UI

```bash
/mgui_list
```

### 4. HTML资源包结构

资源包应包含以下文件：

```
ui-pack.zip
├── main.html          # 主HTML文件（必需）
├── styles.css         # CSS样式文件（可选）
├── script.js          # JavaScript文件（可选）
├── assets/            # 资源文件夹（可选）
│   ├── images/
│   └── fonts/
└── md.json            # 元数据文件（可选）
```

### 5. JavaScript API

在HTML中可以使用以下JS接口：

```javascript
// 关闭当前UI
mgui.close();

// 获取游戏截图（返回URL）
const screenshotUrl = mgui.getScreenshot();

// 执行客户端命令
mgui.executeCommand('/say Hello from UI!');

// 发送聊天消息
mgui.sendMessage('这是一条消息');

// 获取UI信息
const info = mgui.getUiInfo();
console.log(info); // {"uiId":"...", "command":"...", "playerName":"..."}
```

---

## 开发指南

### 编译项目

```bash
# Windows
gradlew.bat build

# Linux/Mac
./gradlew build
```

编译后的文件位于：`build/libs/mgui-1.0.0.jar`

### 运行测试

```bash
# 运行客户端
gradlew.bat runClient

# 运行服务器
gradlew.bat runServer
```

---

## 通信协议

### 服务器 → 客户端

#### 打开UI (`open_ui`)
```json
{
  "type": "open_ui",
  "uiId": "my_ui",
  "command": "mygui:open",
  "resourcePackUrl": "http://example.com/ui.zip"
}
```

#### 关闭UI (`close_ui`)
```json
{
  "type": "close_ui"
}
```

#### UI注册通知 (`ui_registered`)
```json
{
  "type": "ui_registered",
  "command": "mygui:open",
  "uiId": "my_ui",
  "description": "我的UI"
}
```

### 客户端 → 服务器

#### UI关闭通知
```json
{
  "type": "ui_closed",
  "uiId": "my_ui"
}
```

#### 截图就绪通知
```json
{
  "type": "screenshot_ready"
}
```

---

## TODO

### 高优先级
- [ ] 集成完整的HTML渲染引擎（JCEF或JavaFX WebView）
- [ ] 完善CSS样式支持
- [ ] 实现JavaScript完整桥接
- [ ] 优化截图性能

### 中优先级
- [ ] 支持资源包热重载
- [ ] 添加UI动画效果
- [ ] 实现缓存清理机制
- [ ] 支持多语言

### 低优先级
- [ ] UI编辑器工具
- [ ] 主题系统
- [ ] 性能监控
- [ ] 开发者文档

---

## 注意事项

1. **HTML渲染**：当前版本使用简化渲染，完整的HTML/CSS/JS支持需要集成JCEF或JavaFX
2. **截图性能**：截图操作可能影响帧率，建议在UI打开前异步执行
3. **网络带宽**：资源包应尽量压缩，避免过大
4. **安全性**：JS接口仅暴露安全的功能，不直接访问文件系统

---

## 技术栈

- **Minecraft**: 1.21.8
- **Fabric Loader**: >=0.19.2
- **Fabric API**: 最新版
- **Java**: 21+
- **网络**: Fabric Networking API v1
- **渲染**: Minecraft GuiGraphics（待升级为WebView）

---

## 许可证

本项目采用 MIT 许可证

---

## 贡献

欢迎提交 Issue 和 Pull Request！

---

## 联系方式

- GitHub: https://github.com/mgui-project/mgui
- Email: contact@mgui.dev
