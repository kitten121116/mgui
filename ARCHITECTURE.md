# MGUI 架构详解

## 系统架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Minecraft 游戏环境                            │
└─────────────────────────────────────────────────────────────────────┘
                              ▲          ▲
                              │          │
                    Fabric    │          │   Fabric
                    Server    │          │   Client
                    Side      │          │   Side
                              │          │
        ┌─────────────────────┴──┐   ┌───┴──────────────────────┐
        │   MGuiServerMod.java   │   │   MGuiClientMod.java     │
        │                        │   │                          │
        │ • 命令注册             │   │ • 网络监听               │
        │ • UI资源管理           │◄──┼──► • 资源下载            │
        │ • 玩家管理             │   │ • 截图管理               │
        │ • 网络通信             │   │ • UI渲染                 │
        └──────────┬─────────────┘   └──────────┬───────────────┘
                   │                             │
                   │                             │
        ┌──────────▼─────────────┐   ┌──────────▼───────────────┐
        │   MGuiApi.java         │   │   MGuiHtmlScreen.java    │
        │   (业务mod使用)        │   │                          │
        │                        │   │ • HTML加载               │
        │ • registerUi()         │   │ • JS桥接                 │
        │ • openUiForPlayer()    │   │ • 界面渲染               │
        └────────────────────────┘   └──────────┬───────────────┘
                                                │
                                      ┌─────────▼──────────┐
                                      │   JsBridge.java    │
                                      │                    │
                                      │ • close()          │
                                      │ • getScreenshot()  │
                                      │ • executeCommand() │
                                      │ • sendMessage()    │
                                      │ • getUiInfo()      │
                                      └─────────┬──────────┘
                                                │
                                    ┌───────────▼────────────┐
                                    │ ScreenshotManager.java │
                                    │                        │
                                    │ • captureScreenshot()  │
                                    │ • saveScreenshot()     │
                                    │ • cleanup()            │
                                    └────────────────────────┘
```

---

## 数据流图

### 1. UI注册流程

```
业务Mod                    MGuiServerMod              MGuiClientMod
   │                           │                           │
   │  registerUi()             │                           │
   ├──────────────────────────►│                           │
   │  (UI_REGISTER_CHANNEL)    │                           │
   │                           │                           │
   │                           │  存储映射关系              │
   │                           │  command ↔ uiId           │
   │                           │                           │
   │                           │  ui_registered 广播       │
   │                           ├──────────────────────────►│
   │                           │  (SERVER_TO_CLIENT)       │
   │                           │                           │
   │                           │                           │ 记录可用UI
```

### 2. UI打开流程

```
管理员/业务逻辑          MGuiServerMod              MGuiClientMod
   │                           │                           │
   │  /mgui_open player cmd    │                           │
   ├──────────────────────────►│                           │
   │                           │                           │
   │                           │  查找command对应的UI      │
   │                           │                           │
   │                           │  open_ui 消息             │
   │                           ├──────────────────────────►│
   │                           │  (SERVER_TO_CLIENT)       │
   │                           │                           │
   │                           │                           │ 1. 截取游戏画面
   │                           │                           │ 2. 下载资源包
   │                           │                           │ 3. 显示HTML界面
   │                           │                           │
   │                           │                           │ ui_closed 通知
   │                           │◄──────────────────────────┤
   │                           │  (CLIENT_REQUEST)         │
```

### 3. JS调用流程

```
HTML/JavaScript          JsBridge.java           Minecraft客户端
   │                           │                           │
   │  mgui.executeCommand()    │                           │
   ├──────────────────────────►│                           │
   │                           │                           │
   │                           │  解析命令                  │
   │                           │                           │
   │                           │  Commands.performCommand()│
   │                           ├──────────────────────────►│
   │                           │                           │
   │                           │                           │ 执行命令
   │                           │                           │
   │  结果返回                  │◄──────────────────────────┤
   │◄──────────────────────────┤                           │
```

---

## 模块职责

### 服务端模块

#### MGuiServerMod.java
**职责：**
- 初始化服务器端功能
- 注册网络通道监听器
- 管理命令与UI的映射关系
- 处理UI注册请求（来自业务mod）
- 向客户端发送UI打开指令
- 提供管理员命令（/mgui_open, /mgui_list）

**关键方法：**
```java
- onInitializeServer()           // 初始化入口
- registerNetworkChannels()      // 注册网络监听
- handleUiRegistration()         // 处理UI注册
- openUiForPlayer()              // 为玩家打开UI
- broadcastUiRegistration()      // 广播UI注册信息
```

#### MGuiApi.java
**职责：**
- 为业务mod提供简洁的API
- 封装网络通信细节

**关键方法：**
```java
- registerUi(player, command, uiId, url, desc)  // 注册UI
- openUiForPlayer(player, command)              // 打开UI
```

---

### 客户端模块

#### MGuiClientMod.java
**职责：**
- 初始化客户端功能
- 注册网络监听器
- 处理服务器消息（open_ui, close_ui等）
- 协调资源下载和UI显示流程

**关键方法：**
```java
- onInitializeClient()           // 初始化入口
- handleServerMessage()          // 处理服务器消息
- handleOpenUi()                 // 处理打开UI指令
- handleCloseUi()                // 处理关闭UI指令
```

#### MGuiHtmlScreen.java
**职责：**
- 继承Minecraft Screen类
- 加载和显示HTML内容
- 初始化JS桥接
- 处理用户交互

**关键方法：**
```java
- init()                         // 屏幕初始化
- render()                       // 渲染界面
- closeFromJs()                  // JS触发的关闭
- openScreen()                   // 静态打开方法
```

#### JsBridge.java
**职责：**
- 提供JavaScript可调用的原生方法
- 桥接HTML和Minecraft客户端
- 处理JS调用请求

**关键方法：**
```java
- close()                        // 关闭UI
- getScreenshot()                // 获取截图URL
- executeCommand(cmd)            // 执行命令
- sendMessage(msg)               // 发送消息
- getUiInfo()                    // 获取UI信息
- notifyUiClosed()               // 通知服务器UI关闭
```

#### ScreenshotManager.java
**职责：**
- 截取当前游戏画面
- 保存截图到缓存目录
- 管理截图生命周期

**关键方法：**
```java
- captureScreenshot(callback)    // 异步截图
- saveScreenshot()               // 保存截图文件
- getLatestScreenshotPath()      // 获取最新截图路径
- cleanup()                      // 清理旧截图
```

#### MGuiResourcePackManager.java
**职责：**
- 下载HTML资源包（ZIP格式）
- 解压到本地缓存
- 管理资源版本和缓存

**关键方法：**
```java
- downloadResourcePack(uiId, url, callback)  // 下载资源包
- getHtmlPath(uiId)              // 获取HTML文件路径
- cleanup()                      // 清理缓存
```

#### MGuiNetworkHandler.java
**职责：**
- 封装客户端网络发送逻辑
- 提供便捷的消息发送方法

**关键方法：**
```java
- sendMessageToServer(type, data)  // 发送通用消息
- sendScreenshotReady()            // 发送截图就绪通知
- sendUiClosed(uiId)               // 发送UI关闭通知
```

---

## 通信协议详解

### 网络通道定义

```java
// 常量定义在 MGuiConstants.java
UI_REGISTER_CHANNEL = "mgui:ui_register"        // 业务mod → 服务器
SERVER_TO_CLIENT_CHANNEL = "mgui:s2c"           // 服务器 → 客户端
CLIENT_TO_SERVER_CHANNEL = "mgui:c2s"           // 客户端 → 服务器
CLIENT_REQUEST_CHANNEL = "mgui:client_request"  // 客户端请求 → 服务器
```

### 消息格式

所有消息使用JSON格式，通过Fabric的CustomPacketPayload传输。

#### 1. UI注册（业务mod → 服务器）

```json
{
  "command": "mygui:shop",
  "uiId": "shop_ui_001",
  "resourcePackUrl": "http://example.com/shop.zip",
  "description": "商店界面"
}
```

#### 2. 打开UI（服务器 → 客户端）

```json
{
  "type": "open_ui",
  "uiId": "shop_ui_001",
  "command": "mygui:shop",
  "resourcePackUrl": "http://example.com/shop.zip"
}
```

#### 3. 关闭UI（服务器 → 客户端）

```json
{
  "type": "close_ui"
}
```

#### 4. UI注册广播（服务器 → 客户端）

```json
{
  "type": "ui_registered",
  "command": "mygui:shop",
  "uiId": "shop_ui_001",
  "description": "商店界面"
}
```

#### 5. UI关闭通知（客户端 → 服务器）

```json
{
  "type": "ui_closed",
  "uiId": "shop_ui_001"
}
```

#### 6. 截图就绪（客户端 → 服务器）

```json
{
  "type": "screenshot_ready"
}
```

---

## 文件组织结构

```
mcgui/
├── resourcepacks/                 # 资源包缓存目录
│   ├── ui_id_1/
│   │   ├── main.html
│   │   ├── styles.css
│   │   └── script.js
│   └── ui_id_2/
│       └── main.html
│
├── screenshots/                   # 截图缓存目录
│   ├── latest_1234567890.png
│   └── latest_0987654321.png
│
└── logs/                          # 日志目录
    └── latest.log
```

---

## 扩展点

### 1. 添加新的JS接口

在 `JsBridge.java` 中添加方法：

```java
/**
 * 示例：获取玩家位置
 */
public String getPlayerPosition() {
    if (minecraft.player != null) {
        return String.format("%.2f, %.2f, %.2f",
            minecraft.player.getX(),
            minecraft.player.getY(),
            minecraft.player.getZ());
    }
    return "0, 0, 0";
}
```

在HTML中调用：
```javascript
const pos = mgui.getPlayerPosition();
console.log('当前位置:', pos);
```

### 2. 自定义UI动画

在CSS中添加动画：

```css
@keyframes fadeIn {
    from { opacity: 0; transform: scale(0.9); }
    to { opacity: 1; transform: scale(1); }
}

.container {
    animation: fadeIn 0.3s ease-out;
}
```

### 3. 集成第三方库

在HTML中引入CDN：

```html
<script src="https://cdn.jsdelivr.net/npm/vue@3/dist/vue.global.js"></script>
<script src="https://cdn.tailwindcss.com"></script>
```

---

## 性能优化建议

1. **资源包压缩**
   - 使用gzip压缩HTML/CSS/JS
   - 图片使用WebP格式
   - 目标：< 2MB

2. **截图优化**
   - 降低截图分辨率（如50%）
   - 异步执行，不阻塞主线程
   - 及时清理旧截图

3. **缓存策略**
   - 资源包按uiId缓存，避免重复下载
   - 使用ETag或Last-Modified验证更新
   - 定期清理未使用的缓存

4. **网络优化**
   - 批量发送消息，减少包数量
   - 使用二进制协议替代JSON（可选）
   - 实现消息队列，避免拥堵

---

## 安全考虑

1. **JS沙箱**
   - 限制文件系统访问
   - 禁止执行任意Java代码
   - 仅暴露安全的API

2. **资源包验证**
   - Hash校验确保完整性
   - 检查文件大小限制
   - 扫描恶意脚本

3. **权限控制**
   - `/mgui_open` 需要OP权限
   - 业务mod注册需要授权
   - 客户端命令执行受游戏权限限制

---

**架构版本：** 1.0.0  
**最后更新：** 2026-05-22
