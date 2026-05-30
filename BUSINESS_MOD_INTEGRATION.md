# 业务Mod集成指南

## 概述

本文档说明如何为你的业务Mod集成MGUI系统，实现自定义HTML界面。

---

## 集成流程

### 1️⃣ 准备资源包

#### 目录结构
```
my-ui-pack/
├── main.html          # 主HTML文件（必需）
├── styles.css         # CSS样式（可选）
├── script.js          # JavaScript（可选）
└── assets/            # 资源文件夹（可选）
    ├── images/
    └── fonts/
```

#### 示例 main.html
```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>我的UI</title>
    <style>
        body {
            margin: 0;
            padding: 40px;
            font-family: Arial, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
        }
        .container {
            max-width: 600px;
            margin: 0 auto;
            background: rgba(0, 0, 0, 0.6);
            padding: 30px;
            border-radius: 15px;
        }
        button {
            padding: 15px 30px;
            margin: 10px;
            background: #4CAF50;
            color: white;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            font-size: 16px;
        }
        button:hover {
            background: #45a049;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>欢迎来到我的UI</h1>
        <button onclick="doAction()">执行操作</button>
        <button onclick="mgui.close()">关闭</button>
    </div>
    
    <script>
        function doAction() {
            mgui.executeCommand('/give @p diamond 1');
            mgui.sendMessage('§a已获得钻石！');
        }
    </script>
</body>
</html>
```

#### 打包为ZIP
```bash
# Windows (PowerShell)
Compress-Archive -Path my-ui-pack/* -DestinationPath my-ui.zip

# Linux/Mac
cd my-ui-pack && zip -r ../my-ui.zip .
```

---

### 2️⃣ 部署资源包到服务器

将 `my-ui.zip` 复制到服务器的 `mguiregs/` 目录：

```
server_root/
├── mods/
│   └── your-business-mod.jar
├── mguiregs/              # ← 创建此目录
│   └── my-ui.zip         # ← 放置ZIP文件
└── ...
```

**注意：** `mguiregs/` 目录需要手动创建，或在模组初始化时自动创建。

---

### 3️⃣ 在业务Mod中注册UI

#### 添加依赖

在 `build.gradle` 中添加MGUI依赖：

```gradle
dependencies {
    // 如果MGUI是独立mod，使用 compileOnly
    compileOnly files("libs/mgui-1.0.0.jar")
}
```

#### 注册代码示例

```java
package com.example.mymod;

import com.example.wgewge.api.MGuiApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyBusinessMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("my_business_mod");
    
    @Override
    public void onInitialize() {
        LOGGER.info("我的业务Mod初始化...");
        
        // 监听玩家连接事件，自动注册UI
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            
            // 注册UI
            registerMyUi(player);
        });
        
        LOGGER.info("我的业务Mod初始化完成");
    }
    
    /**
     * 注册UI到MGUI系统
     */
    private void registerMyUi(ServerPlayer player) {
        try {
            MGuiApi.registerUi(
                player,
                "mymod:shop",              // 命令：触发UI的命令
                "my-ui.zip",               // ZIP文件名（位于 mguiregs/ 目录）
                "shop",                    // 别名：简称，方便调用
                "商店界面"                  // 描述：UI的描述信息
            );
            
            LOGGER.info("UI注册成功: mymod:shop");
            
        } catch (Exception e) {
            LOGGER.error("UI注册失败: {}", e.getMessage(), e);
        }
    }
}
```

---

### 4️⃣ 测试UI

在游戏中执行命令：

```bash
# 为自己打开UI
/mgui_open @s mymod:shop

# 为其他玩家打开UI（需要OP权限）
/mgui_open Steve mymod:shop

# 查看所有已注册的UI
/mgui_list
```

---

## 完整示例：商店系统

### 项目结构
```
my-shop-mod/
├── src/main/java/com/example/myshop/
│   ├── MyShopMod.java           # 主类
│   └── ShopHandler.java         # 商店逻辑
├── resources/
│   └── fabric.mod.json
└── ui-resources/
    └── shop-ui/
        ├── main.html
        ├── styles.css
        └── script.js
```

### MyShopMod.java
```java
package com.example.myshop;

import com.example.wgewge.api.MGuiApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyShopMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("my_shop");
    
    @Override
    public void onInitialize() {
        LOGGER.info("商店系统初始化...");
        
        // 注册玩家连接事件
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            registerShopUi(handler.getPlayer());
        });
        
        // 注册自定义命令
        registerCommands();
        
        LOGGER.info("商店系统初始化完成");
    }
    
    private void registerShopUi(ServerPlayer player) {
        MGuiApi.registerUi(
            player,
            "shop:open",           // 命令
            "shop-ui.zip",         // ZIP文件
            "shop",                // 别名
            "游戏商店"              // 描述
        );
    }
    
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            // 注册 /shop 命令
            dispatcher.register(
                Commands.literal("shop")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        MGuiApi.openUiForPlayer(player, "shop:open");
                        return 1;
                    })
            );
        });
    }
}
```

### shop-ui/main.html
```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>游戏商店</title>
    <style>
        body {
            margin: 0;
            padding: 40px;
            background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
            font-family: 'Microsoft YaHei', Arial;
        }
        .shop-container {
            max-width: 800px;
            margin: 0 auto;
            background: rgba(255, 255, 255, 0.95);
            padding: 30px;
            border-radius: 20px;
            box-shadow: 0 10px 40px rgba(0,0,0,0.3);
        }
        h1 {
            text-align: center;
            color: #333;
            margin-bottom: 30px;
        }
        .item-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 20px;
            margin-bottom: 30px;
        }
        .item-card {
            background: #f8f9fa;
            padding: 20px;
            border-radius: 10px;
            text-align: center;
            cursor: pointer;
            transition: transform 0.2s;
        }
        .item-card:hover {
            transform: translateY(-5px);
            box-shadow: 0 5px 15px rgba(0,0,0,0.2);
        }
        .item-icon {
            font-size: 48px;
            margin-bottom: 10px;
        }
        .item-name {
            font-size: 18px;
            font-weight: bold;
            color: #333;
            margin-bottom: 5px;
        }
        .item-price {
            color: #f5576c;
            font-size: 16px;
        }
        .close-btn {
            display: block;
            width: 100%;
            padding: 15px;
            background: #6c757d;
            color: white;
            border: none;
            border-radius: 10px;
            font-size: 18px;
            cursor: pointer;
        }
        .close-btn:hover {
            background: #5a6268;
        }
    </style>
</head>
<body>
    <div class="shop-container">
        <h1>🛒 游戏商店</h1>
        
        <div class="item-grid">
            <div class="item-card" onclick="buyItem('diamond', 100)">
                <div class="item-icon">💎</div>
                <div class="item-name">钻石</div>
                <div class="item-price">100 金币</div>
            </div>
            
            <div class="item-card" onclick="buyItem('emerald', 150)">
                <div class="item-icon"> Emerald </div>
                <div class="item-name">绿宝石</div>
                <div class="item-price">150 金币</div>
            </div>
            
            <div class="item-card" onclick="buyItem('gold', 80)">
                <div class="item-icon">🥇</div>
                <div class="item-name">金锭</div>
                <div class="item-price">80 金币</div>
            </div>
        </div>
        
        <button class="close-btn" onclick="mgui.close()">关闭商店</button>
    </div>
    
    <script>
        function buyItem(item, price) {
            // 检查玩家金币（这里简化处理）
            mgui.executeCommand('/give @p ' + item + ' 1');
            mgui.sendMessage('§a购买成功！花费 ' + price + ' 金币');
        }
    </script>
</body>
</html>
```

---

## API参考

### MGuiApi.registerUi()

```java
public static void registerUi(
    ServerPlayer player,      // 玩家对象
    String command,           // 触发命令（如 "mymod:shop"）
    String zipFileName,       // ZIP文件名（位于 mguiregs/ 目录）
    String alias,             // 别名（简称，如 "shop"）
    String description        // 描述信息
)
```

**参数说明：**
- `command`: 完整的命令标识符，建议使用命名空间格式
- `zipFileName`: ZIP文件必须存在于服务器的 `mguiregs/` 目录
- `alias`: 简短的别名，用于日志和调试
- `description`: UI的描述，会显示在 `/mgui_list` 中

### MGuiApi.openUiForPlayer()

```java
public static void openUiForPlayer(
    ServerPlayer player,      // 目标玩家
    String command            // UI对应的命令
)
```

**用途：** 直接为玩家打开指定的UI，无需玩家输入命令。

---

## 常见问题

### Q: ZIP文件放在哪里？
A: 放在服务器根目录的 `mguiregs/` 文件夹下。

### Q: 可以动态更新UI吗？
A: 可以。替换 `mguiregs/` 中的ZIP文件，然后重新注册UI即可。

### Q: HTML支持哪些功能？
A: 支持标准HTML/CSS/JS。当前版本渲染引擎为基础实现，完整WebView支持待集成。

### Q: JS接口有哪些？
A: 
- `mgui.close()` - 关闭UI
- `mgui.getScreenshot()` - 获取游戏截图
- `mgui.executeCommand(cmd)` - 执行游戏命令
- `mgui.sendMessage(msg)` - 发送聊天消息
- `mgui.getUiInfo()` - 获取UI信息

### Q: 如何调试？
A: 查看服务器和客户端的日志文件 `logs/latest.log`，搜索关键词 `MGUI` 或 `mgui`。

---

## 最佳实践

1. **ZIP文件大小**：建议小于2MB，避免网络传输过慢
2. **命令命名**：使用命名空间格式，如 `mymod:ui_name`
3. **资源优化**：压缩图片，使用WebP格式
4. **错误处理**：在注册UI时添加try-catch
5. **日志记录**：记录关键操作，便于调试

---

**祝你开发愉快！** 🎉
