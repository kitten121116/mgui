# MGUI 新架构说明 - ZIP资源包下发模式

## 📋 架构变更概览

### 之前的设计
- ❌ 业务mod提供HTTP URL
- ❌ 客户端从HTTP服务器下载资源包
- ❌ 依赖外部Web服务器

### 现在的设计 ✅
- ✅ 业务mod解压资源包到服务器 `mguiregs/` 目录
- ✅ 服务器直接发送ZIP数据给客户端
- ✅ 客户端接收ZIP并解压渲染
- ✅ 无需外部Web服务器

---

## 🔄 完整工作流程

```
┌──────────────┐
│  业务 Mod    │
└──────┬───────┘
       │
       │ 1. 准备 UI 资源（HTML/CSS/JS）
       │ 2. 打包为 ZIP 文件
       │ 3. 放置到服务器 mguiregs/ 目录
       ▼
┌──────────────────┐
│  mguiregs/       │
│  └── my-ui.zip   │
└──────┬───────────┘
       │
       │ 4. 调用 MGuiApi.registerUi()
       │    - command: "mymod:shop"
       │    - zipFileName: "my-ui.zip"
       │    - alias: "shop"
       │    - description: "商店界面"
       ▼
┌──────────────────────┐
│  MGuiServerMod       │
│                      │
│  • 验证ZIP文件存在    │
│  • 存储映射关系       │
│    command → zip     │
│  • 广播注册信息       │
└──────┬───────────────┘
       │
       │ 5. 玩家输入命令或管理员执行
       │    /mgui_open @s mymod:shop
       ▼
┌──────────────────────┐
│  检测到命令匹配       │
│                      │
│  • 读取ZIP文件        │
│  • 分块编码(Base64)   │
│  • 发送给客户端       │
└──────┬───────────────┘
       │
       │ 6. 通过网络通道发送
       │    - open_ui (元数据)
       │    - zip_chunk (数据块1)
       │    - zip_chunk (数据块2)
       │    - ...
       │    - zip_complete (完成标记)
       ▼
┌──────────────────────┐
│  MGuiClientMod       │
│                      │
│  • 接收数据块         │
│  • 缓存到内存         │
│  • 合并所有块         │
└──────┬───────────────┘
       │
       │ 7. 解码并解压ZIP
       │    到本地缓存目录
       ▼
┌──────────────────────┐
│  客户端缓存目录       │
│  mgui/resourcepacks/ │
│  └── shop/           │
│      ├── main.html   │
│      ├── styles.css  │
│      └── script.js   │
└──────┬───────────────┘
       │
       │ 8. 加载HTML并渲染
       │    显示UI界面
       ▼
┌──────────────────────┐
│  玩家看到 HTML UI    │
│                      │
│  • 可与JS交互         │
│  • 可执行游戏命令     │
│  • 可关闭返回游戏     │
└──────────────────────┘
```

---

## 📂 目录结构

### 服务器端
```
server_root/
├── mods/
│   ├── mgui-1.0.0.jar          # MGUI模组
│   └── my-business-mod.jar     # 业务mod
├── mguiregs/                    # ← 资源包目录（新建）
│   ├── shop-ui.zip             # 商店UI
│   ├── inventory-ui.zip        # 背包UI
│   └── settings-ui.zip         # 设置UI
└── ...
```

### 客户端
```
minecraft/
├── mods/
│   └── mgui-1.0.0.jar          # MGUI模组
├── mgui/
│   ├── resourcepacks/           # UI缓存目录
│   │   ├── shop/               # 解压后的文件
│   │   │   ├── main.html
│   │   │   ├── styles.css
│   │   │   └── script.js
│   │   └── inventory/
│   └── screenshots/             # 截图缓存
│       └── latest_xxx.png
└── ...
```

---

## 🔧 代码实现要点

### 1. 服务器端 - ZIP文件读取和发送

```java
// MGuiServerMod.java

public static int openUiForPlayer(ServerPlayer player, String command) {
    UiResourceInfo uiInfo = commandUiMap.get(command);
    
    // 读取ZIP文件
    String zipPath = RESOURCE_PACK_DIR + uiInfo.zipFileName;
    byte[] zipData = Files.readAllBytes(Paths.get(zipPath));
    
    // 发送元数据
    JsonObject meta = new JsonObject();
    meta.addProperty("type", "open_ui");
    meta.addProperty("alias", uiInfo.alias);
    meta.addProperty("command", uiInfo.command);
    ServerPlayNetworking.send(player, createPayload(meta.toString()));
    
    // 分块发送ZIP数据
    sendZipDataToClient(player, zipData);
    
    return 1;
}

private void sendZipDataToClient(ServerPlayer player, byte[] zipData) {
    int chunkSize = 32000; // 每块32KB
    int totalChunks = (int) Math.ceil((double) zipData.length / chunkSize);
    
    for (int i = 0; i < totalChunks; i++) {
        int start = i * chunkSize;
        int end = Math.min(start + chunkSize, zipData.length);
        byte[] chunk = Arrays.copyOfRange(zipData, start, end);
        
        JsonObject chunkMsg = new JsonObject();
        chunkMsg.addProperty("type", "zip_chunk");
        chunkMsg.addProperty("chunkIndex", i);
        chunkMsg.addProperty("totalChunks", totalChunks);
        chunkMsg.addProperty("data", Base64.getEncoder().encodeToString(chunk));
        
        ServerPlayNetworking.send(player, createPayload(chunkMsg.toString()));
    }
    
    // 发送完成标记
    JsonObject completeMsg = new JsonObject();
    completeMsg.addProperty("type", "zip_complete");
    ServerPlayNetworking.send(player, createPayload(completeMsg.toString()));
}
```

### 2. 客户端 - ZIP数据接收和解压

```java
// MGuiClientMod.java

private List<byte[]> zipChunks = new ArrayList<>();
private String currentUiAlias = "";

private void handleZipChunk(JsonObject json) {
    int chunkIndex = json.get("chunkIndex").getAsInt();
    String base64Data = json.get("data").getAsString();
    
    // 解码Base64
    byte[] chunkData = Base64.getDecoder().decode(base64Data);
    
    // 缓存数据块
    while (zipChunks.size() <= chunkIndex) {
        zipChunks.add(null);
    }
    zipChunks.set(chunkIndex, chunkData);
}

private void handleZipComplete() {
    // 合并所有数据块
    int totalSize = zipChunks.stream().mapToInt(c -> c.length).sum();
    byte[] zipData = new byte[totalSize];
    int offset = 0;
    for (byte[] chunk : zipChunks) {
        System.arraycopy(chunk, 0, zipData, offset, chunk.length);
        offset += chunk.length;
    }
    
    // 解压ZIP
    String uiPath = MGuiResourcePackManager.getInstance()
        .extractZipToCache(currentUiAlias, zipData);
    
    // 打开UI
    MGuiHtmlScreen.openScreen(currentUiAlias, currentCommand);
    
    // 清空缓冲
    zipChunks.clear();
}
```

### 3. 资源包管理器 - ZIP解压

```java
// MGuiResourcePackManager.java

public String extractZipToCache(String alias, byte[] zipData) {
    String uiDir = RESOURCE_PACK_BASE_PATH + alias;
    Path uiPath = Paths.get(uiDir);
    Files.createDirectories(uiPath);
    
    // 写入临时ZIP文件
    Path tempZip = uiPath.resolve("temp.zip");
    Files.write(tempZip, zipData);
    
    // 解压
    unzipFile(tempZip.toFile(), uiPath.toFile());
    
    // 删除临时文件
    Files.deleteIfExists(tempZip);
    
    return uiDir;
}

private void unzipFile(File zipFile, File destDir) throws IOException {
    try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFile))) {
        ZipEntry entry;
        while ((entry = zipIn.getNextEntry()) != null) {
            Path filePath = Paths.get(destDir.getAbsolutePath(), entry.getName());
            
            if (!entry.isDirectory()) {
                Files.createDirectories(filePath.getParent());
                try (OutputStream out = Files.newOutputStream(filePath)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = zipIn.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
            }
            zipIn.closeEntry();
        }
    }
}
```

---

## 📊 网络消息协议

### 消息类型

| 类型 | 方向 | 说明 |
|------|------|------|
| `open_ui` | 服务器→客户端 | UI打开指令（元数据） |
| `zip_chunk` | 服务器→客户端 | ZIP数据块 |
| `zip_complete` | 服务器→客户端 | ZIP传输完成 |
| `ui_registered` | 服务器→客户端 | UI注册通知 |
| `close_ui` | 服务器→客户端 | 关闭UI指令 |
| `ui_closed` | 客户端→服务器 | UI关闭通知 |

### open_ui 消息格式

```json
{
  "type": "open_ui",
  "alias": "shop",
  "command": "mymod:shop",
  "zipFileName": "shop-ui.zip",
  "description": "商店界面"
}
```

### zip_chunk 消息格式

```json
{
  "type": "zip_chunk",
  "chunkIndex": 0,
  "totalChunks": 5,
  "data": "UEsDBBQAAAAI..."  // Base64编码的ZIP数据
}
```

---

## ⚡ 性能优化

### 1. 分块传输
- 每块大小：32KB
- 避免单个网络包过大
- 支持断点续传（未来扩展）

### 2. Base64编码
- 简单可靠
- 兼容性好
- 体积增加约33%（可接受）

### 3. 客户端缓存
- 按alias缓存解压后的文件
- 避免重复解压
- 下次打开直接使用缓存

### 4. 异步处理
- ZIP解压在后台线程
- 不阻塞游戏主线程
- 截图操作异步执行

---

## 🔒 安全性考虑

### 1. 文件验证
```java
// 验证ZIP文件存在
File zipFile = new File(RESOURCE_PACK_DIR + zipFileName);
if (!zipFile.exists()) {
    LOGGER.error("资源包文件不存在: {}", zipPath);
    return;
}
```

### 2. 路径安全检查
```java
// 防止路径遍历攻击
Path filePath = Paths.get(destDir.getAbsolutePath(), entry.getName());
if (!filePath.startsWith(destDir.getAbsolutePath())) {
    LOGGER.warn("检测到非法路径: {}", entry.getName());
    continue;
}
```

### 3. 文件大小限制
```java
// 限制ZIP文件大小（例如10MB）
if (zipData.length > 10 * 1024 * 1024) {
    LOGGER.error("ZIP文件过大: {} bytes", zipData.length);
    return;
}
```

---

## 📝 业务Mod使用示例

### 最简单的集成

```java
// 在玩家连接时注册UI
ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
    MGuiApi.registerUi(
        handler.getPlayer(),
        "mymod:test",           // 命令
        "test-ui.zip",          // ZIP文件
        "test",                 // 别名
        "测试UI"                // 描述
    );
});
```

### 自定义命令触发

```java
// 注册 /mycommand 命令
CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
    dispatcher.register(
        Commands.literal("mycommand")
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                MGuiApi.openUiForPlayer(player, "mymod:test");
                return 1;
            })
    );
});
```

---

## 🎯 优势总结

### 相比HTTP方案的优势

1. **无需外部服务器** 🚀
   - 不需要搭建Web服务器
   - 减少部署复杂度
   - 降低维护成本

2. **更好的安全性** 🔒
   - 资源包在服务器控制下
   - 避免URL注入攻击
   - 可以验证文件完整性

3. **更快的加载速度** ⚡
   - 局域网内传输
   - 无HTTP开销
   - 直接内存传输

4. **更简单的配置** 🛠️
   - 只需放置ZIP文件
   - 无需配置URL
   - 自动管理缓存

5. **离线可用** 📴
   - 不依赖外部网络
   - 适合局域网服务器
   - 更稳定的体验

---

## 🚀 快速开始

1. **准备ZIP资源包**
   ```bash
   # 创建UI文件
   mkdir my-ui
   echo "<html>...</html>" > my-ui/main.html
   
   # 打包为ZIP
   cd my-ui && zip -r ../my-ui.zip .
   ```

2. **部署到服务器**
   ```bash
   cp my-ui.zip server/mguiregs/
   ```

3. **在业务mod中注册**
   ```java
   MGuiApi.registerUi(player, "mymod:ui", "my-ui.zip", "ui", "我的UI");
   ```

4. **在游戏中打开**
   ```bash
   /mgui_open @s mymod:ui
   ```

---

**架构版本：** 2.0.0 (ZIP下发模式)  
**更新时间：** 2026-05-22
