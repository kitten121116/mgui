# MGUI 快速开始指南

## 5分钟上手

### 1️⃣ 编译模组

```bash
gradlew.bat build
```

编译后的文件位于：`build/libs/mgui-1.0.0.jar`

---

### 2️⃣ 安装到服务器和客户端

**服务器端：**
```
server/mods/
└── mgui-1.0.0.jar
```

**客户端：**
```
client/mods/
└── mgui-1.0.0.jar
```

---

### 3️⃣ 启动测试

**启动服务器：**
```bash
gradlew.bat runServer
```

**启动客户端：**
```bash
gradlew.bat runClient
```

---

### 4️⃣ 创建你的第一个UI

#### 步骤1：准备HTML资源包

创建 `my-ui.zip`，包含：

**main.html**
```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>我的第一个MGUI</title>
    <style>
        body {
            margin: 0;
            padding: 40px;
            font-family: 'Microsoft YaHei', Arial, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            min-height: 100vh;
        }
        .container {
            max-width: 600px;
            margin: 0 auto;
            background: rgba(0, 0, 0, 0.6);
            padding: 30px;
            border-radius: 15px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
        }
        h1 {
            text-align: center;
            margin-bottom: 30px;
            font-size: 32px;
        }
        .button {
            display: block;
            width: 100%;
            padding: 15px;
            margin: 10px 0;
            background: #4CAF50;
            color: white;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            font-size: 18px;
            transition: all 0.3s;
        }
        .button:hover {
            background: #45a049;
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
        }
        .info {
            margin-top: 20px;
            padding: 15px;
            background: rgba(255, 255, 255, 0.1);
            border-radius: 8px;
            font-size: 14px;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🎮 我的第一个MGUI</h1>
        
        <button class="button" onclick="giveDiamond()">
            💎 给我钻石
        </button>
        
        <button class="button" onclick="changeTime()">
            🌅 切换到白天
        </button>
        
        <button class="button" onclick="mgui.close()">
            ❌ 关闭界面
        </button>
        
        <div class="info" id="info">
            点击按钮执行游戏命令
        </div>
    </div>
    
    <script>
        function giveDiamond() {
            mgui.executeCommand('/give @p diamond 10');
            document.getElementById('info').innerHTML = '✅ 已获得10个钻石！';
            mgui.sendMessage('§a成功获得10个钻石！');
        }
        
        function changeTime() {
            mgui.executeCommand('/time set day');
            document.getElementById('info').innerHTML = '✅ 时间已设置为白天！';
            mgui.sendMessage('§e时间已设置为白天');
        }
    </script>
</body>
</html>
```

压缩为 `my-ui.zip`

#### 步骤2：上传资源包

将 `my-ui.zip` 上传到Web服务器，例如：
```
http://localhost:8080/my-ui.zip
```

或使用GitHub Releases、网盘等。

#### 步骤3：注册UI（在业务mod中）

创建一个简单的业务mod或在现有mod中添加：

```java
package com.example.mybusinessmod;

import com.example.wgewge.api.MGuiApi;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public class MyBusinessMod {
    
    public static void init() {
        // 玩家连接时自动注册UI
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            
            // 注册我们的UI
            MGuiApi.registerUi(
                player,
                "mygui:test",                              // 命令
                "my_first_ui",                             // UI ID
                "http://localhost:8080/my-ui.zip",         // 资源包URL
                "我的第一个测试UI"                          // 描述
            );
        });
    }
}
```

---

### 5️⃣ 测试UI

在游戏中执行：

```bash
# 为自己打开UI
/mgui_open @s mygui:test

# 或为其他玩家打开
/mgui_open Steve mygui:test
```

你应该能看到一个漂亮的渐变背景界面，带有三个按钮！

---

## 常用功能示例

### 📸 使用游戏截图作为背景

```html
<script>
    // 获取游戏截图
    const screenshot = mgui.getScreenshot();
    
    // 设置为背景
    document.body.style.backgroundImage = `url(${screenshot})`;
    document.body.style.backgroundSize = 'cover';
    document.body.style.backgroundPosition = 'center';
</script>
```

### 💬 发送聊天消息

```javascript
// 发送普通消息
mgui.sendMessage('这是一条消息');

// 发送彩色消息（使用Minecraft格式代码）
mgui.sendMessage('§a绿色 §b蓝色 §c红色');
```

### ⚙️ 执行复杂命令

```javascript
// 传送玩家
mgui.executeCommand('/tp @p 100 64 100');

// 生成实体
mgui.executeCommand('/summon zombie ~ ~ ~');

// 设置游戏规则
mgui.executeCommand('/gamerule keepInventory true');
```

### 📊 获取UI信息

```javascript
const info = JSON.parse(mgui.getUiInfo());
console.log('UI ID:', info.uiId);
console.log('命令:', info.command);
console.log('玩家:', info.playerName);
```

---

## 调试技巧

### 查看日志

**客户端日志：**
```
logs/latest.log
```

搜索关键词：
- `MGUI` - MGUI系统日志
- `mgui_client` - 客户端特定日志

**服务器日志：**
```
logs/latest.log
```

搜索关键词：
- `mgui_server` - 服务器端日志
- `mgui_api` - API调用日志

### 常见问题

**Q: UI没有显示？**
- 检查资源包URL是否可访问
- 查看客户端日志是否有下载错误
- 确认命令是否正确注册

**Q: JS接口不工作？**
- 当前版本HTML渲染引擎未完全集成
- JS桥接代码已准备好，等待WebView集成

**Q: 截图是黑的？**
- 确保在游戏世界中（不是主菜单）
- 检查是否有渲染权限

---

## 下一步

1. 📖 阅读 [完整文档](MGUI_SERVER_CLIENT_README.md)
2. 🔧 查看 [重构总结](REFACTORING_SUMMARY.md)
3. 💡 学习更多 [JS API用法](#js-api参考)

---

## JS API 参考

| 方法 | 说明 | 示例 |
|------|------|------|
| `mgui.close()` | 关闭当前UI | `mgui.close()` |
| `mgui.getScreenshot()` | 获取游戏截图URL | `const url = mgui.getScreenshot()` |
| `mgui.executeCommand(cmd)` | 执行游戏命令 | `mgui.executeCommand('/say hi')` |
| `mgui.sendMessage(msg)` | 发送聊天消息 | `mgui.sendMessage('Hello!')` |
| `mgui.getUiInfo()` | 获取UI信息（JSON） | `const info = mgui.getUiInfo()` |

---

## 需要帮助？

- 📝 查看文档：[MGUI_SERVER_CLIENT_README.md](MGUI_SERVER_CLIENT_README.md)
- 🐛 报告问题：提交Issue
- 💬 讨论：加入Discord社区

祝你开发愉快！🎉
