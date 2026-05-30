# MGUI 项目重构总结

## 重构概述

已将原项目从"插件+客户端"架构改造为**专注服务器+客户端的Fabric模组**架构。

---

## 主要变更

### 1. 架构调整

**之前：**
- Bukkit插件 + Fabric客户端mod
- 通过Plugin Message通信
- 依赖本地HTTP服务器

**现在：**
- Fabric服务器端mod + Fabric客户端mod
- 通过Fabric Networking API通信
- 纯模组间通信，无需额外插件

### 2. 核心文件结构

#### 服务端（src/main）
```
com.example.wgewge/
├── Template.java                    # 主入口，初始化服务端
├── MGuiConstants.java               # 常量定义
├── server/
│   └── MGuiServerMod.java           # 服务端核心逻辑
└── api/
    └── MGuiApi.java                 # 业务mod使用的API
```

#### 客户端（src/client）
```
com.example.wgewge.client/
├── TemplateClient.java              # 客户端入口
├── MGuiClientMod.java               # 客户端核心逻辑
├── gui/
│   └── MGuiHtmlScreen.java          # HTML屏幕渲染
├── js/
│   └── JsBridge.java                # JS桥接接口
├── screenshot/
│   └── ScreenshotManager.java       # 截图管理器
├── resource/
│   └── MGuiResourcePackManager.java # 资源包管理器
└── network/
    └── MGuiNetworkHandler.java      # 网络处理器
```

### 3. 功能实现

#### ✅ 已完成

1. **服务器端功能**
   - UI资源注册管理
   - 命令与UI映射关系维护
   - `/mgui_open` 强制打开UI命令
   - `/mgui_list` 列出所有UI命令
   - 向客户端发送UI打开指令

2. **客户端功能**
   - 接收服务器UI打开指令
   - HTML资源包下载和管理
   - 游戏画面截图（打开UI前自动截图）
   - HTML界面渲染（基础框架）
   - JS桥接接口实现

3. **JS接口**
   - `mgui.close()` - 关闭UI
   - `mgui.getScreenshot()` - 获取游戏截图URL
   - `mgui.executeCommand(cmd)` - 执行客户端命令
   - `mgui.sendMessage(msg)` - 发送聊天消息
   - `mgui.getUiInfo()` - 获取UI信息

4. **通信协议**
   - 服务器→客户端：open_ui, close_ui, ui_registered
   - 客户端→服务器：ui_closed, screenshot_ready

### 4. 网络通道

```java
UI_REGISTER_CHANNEL = "mgui:ui_register"        // UI注册
SERVER_TO_CLIENT_CHANNEL = "mgui:s2c"           // 服务器到客户端
CLIENT_TO_SERVER_CHANNEL = "mgui:c2s"           // 客户端到服务器
CLIENT_REQUEST_CHANNEL = "mgui:client_request"  // 客户端请求
```

---

## 使用流程

### 场景1：业务Mod注册UI

```java
// 在业务mod的服务端代码中
@EventHandler
public void onServerStarting(FMLServerStartingEvent event) {
    ServerPlayer player = ...; // 获取玩家
    
    // 注册UI
    MGuiApi.registerUi(
        player,
        "mygui:shop",                        // 命令
        "shop_ui_001",                       // UI ID
        "http://example.com/shop-ui.zip",    // 资源包URL
        "商店界面"                            // 描述
    );
}
```

### 场景2：服务器强制打开UI

```bash
# 管理员命令
/mgui_open Steve mygui:shop
```

### 场景3：HTML中使用JS接口

```html
<!DOCTYPE html>
<html>
<head>
    <title>商店</title>
    <style>
        body { 
            background: rgba(0,0,0,0.8); 
            color: white; 
            padding: 20px;
        }
        .btn { 
            padding: 10px 20px; 
            margin: 10px;
            cursor: pointer;
        }
    </style>
</head>
<body>
    <h1>欢迎来到商店</h1>
    
    <button class="btn" onclick="buyItem()">购买商品</button>
    <button class="btn" onclick="mgui.close()">关闭</button>
    
    <script>
        function buyItem() {
            // 执行游戏命令
            mgui.executeCommand('/give @p diamond 1');
            mgui.sendMessage('购买成功！');
        }
        
        // 显示游戏截图作为背景
        const screenshot = mgui.getScreenshot();
        document.body.style.backgroundImage = `url(${screenshot})`;
    </script>
</body>
</html>
```

---

## 技术要点

### 1. Fabric 1.21.8 网络API适配

使用新的Payload系统：
```java
// 注册接收器
ServerPlayNetworking.registerGlobalReceiver(
    CHANNEL_ID,
    (payload, context) -> {
        String data = payload.data().readUtf(32767);
        // 处理数据
    }
);

// 发送消息
ServerPlayNetworking.send(player, new CustomPacketPayload() {
    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(jsonString);
    }
    
    @Override
    public ResourceLocation id() {
        return CHANNEL_ID;
    }
});
```

### 2. 截图实现

使用Minecraft原生渲染系统：
```java
NativeImage image = new NativeImage(width, height, false);
RenderSystem.bindFramebuffer(0);
image.downloadFromFrameBuffer(0, width, height, false);
// 转换为BufferedImage并保存
```

### 3. 资源包管理

- 异步下载ZIP资源包
- 解压到本地缓存目录
- Hash校验确保完整性
- 按需加载避免浪费带宽

---

## 待完善功能

### 高优先级
- [ ] **集成HTML渲染引擎**：当前仅显示占位文本，需要集成JCEF或JavaFX WebView来真正渲染HTML/CSS/JS
- [ ] **完善截图背景**：将截图作为HTML界面的背景显示
- [ ] **JS完整桥接**：实现WebView与JsBridge的完整绑定

### 中优先级
- [ ] 资源包热重载
- [ ] UI动画效果
- [ ] 缓存清理机制
- [ ] 多语言支持

### 低优先级
- [ ] UI可视化编辑器
- [ ] 主题系统
- [ ] 性能监控面板

---

## 编译和测试

### 编译
```bash
gradlew.bat build
```

输出文件：`build/libs/mgui-1.0.0.jar`

### 测试步骤

1. **启动服务器**
   ```bash
   gradlew.bat runServer
   ```

2. **启动客户端**
   ```bash
   gradlew.bat runClient
   ```

3. **测试命令**
   ```
   /mgui_list          # 查看已注册的UI
   /mgui_open <player> <command>  # 打开UI
   ```

---

## 关键改进点

1. **纯Fabric生态**：不再依赖Bukkit插件，完全在Fabric框架内运行
2. **模块化设计**：服务端和客户端职责清晰分离
3. **标准化通信**：使用Fabric官方Networking API
4. **可扩展API**：提供MGuiApi供其他mod使用
5. **现代Web技术**：支持HTML/CSS/JS，降低UI开发门槛

---

## 注意事项

1. **HTML渲染**：当前版本是框架实现，真正的HTML渲染需要额外集成WebView库
2. **性能考虑**：截图操作应在异步线程执行，避免卡顿
3. **资源大小**：HTML资源包应压缩，建议小于5MB
4. **安全性**：JS接口已做安全限制，不会暴露敏感功能

---

## 下一步计划

1. 集成JCEF（Chromium Embedded Framework）实现完整HTML渲染
2. 编写完整的单元测试
3. 创建示例业务mod展示完整用法
4. 优化网络传输效率
5. 添加开发者文档和教程

---

**重构完成时间**：2026-05-22  
**版本**：1.0.0  
**架构**：Fabric Server-Client Mod
