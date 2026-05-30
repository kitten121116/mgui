# MGUI 快速开始指南

## 🚀 5分钟快速上手

### 1. 编译项目
```bash
./gradlew build
```

### 2. 运行游戏
```bash
./gradlew runClient
```

### 3. 测试功能

#### 进入游戏后，你会看到：
```
§6§l欢迎来到游戏！
§e感谢使用 Template 模组！
§bMGUI 系统已就绪
§a使用 /mgui test 打开测试界面
```

#### 测试命令
在聊天栏输入：
```
/mgui test
```

这会打开一个测试 GUI 界面（目前显示占位文本）。

## 📋 核心 API 使用

### 发送插件消息
```java
// 客户端发送消息到服务端
MGuiNetworkManager.sendMessageToServer("{\"type\":\"pmsg\",\"plugin\":\"com.test\",\"msg\":\"hello\"}");
```

### 请求资源包
```java
// 触发资源下载
MGuiResourceManager.getInstance().requestResourcePack("com.example.plugin.1");
```

### 显示消息窗口
```java
JsonObject params = new JsonObject();
params.addProperty("c", "§6"); // 颜色
params.addProperty("b", "2");  // 大小
MGuiMessageWindow.showMessage("欢迎！", params);
```

### 执行命令
```java
// 执行 Minecraft 命令
MGuiCommandExecutor.executeCommand("tp @a 0 0 0");
```

### 显示通知
```java
// Windows 通知
MGuiNotification.showNotification("player123", "你收到了一条消息！");
```

### 打开 GUI
```java
// 打开插件界面
MGuiScreen.openGui("com.example.plugin.1");
```

## 🗂️ 目录结构说明

创建插件资源包时，按照以下结构：

```
com.example.test.1.zip
├── main.html          # 主界面 HTML
├── md.json            # 元数据
└── res/               # 资源文件夹
    ├── style.css      # 样式
    ├── script.js      # 脚本
    └── images/        # 图片
        └── bg.png     # 背景图
```

### md.json 示例
```json
{
  "main-hash": "计算出的 SHA-256 hash",
  "res-hash": "计算出的 SHA-256 hash"
}
```

### main.html 示例
```html
<!DOCTYPE html>
<html>
<head>
    <title>测试界面</title>
    <link rel="stylesheet" href="res/style.css">
</head>
<body>
    <h1>欢迎使用 MGUI</h1>
    <button onclick="sendCommand('tp @s 0 10 0')">传送</button>
    
    <script src="res/script.js"></script>
</body>
</html>
```

## 🔌 协议使用示例

### pmsg:// 协议
```javascript
// 在 HTML 中
window.location.href = 'pmsg://{"plugin":"com.test","msg":"@tp @s 0 10 0"}';
```

### ccmd:// 协议
```javascript
// 直接执行命令
window.location.href = 'ccmd://tp @a 0 0 0';
```

### msgWindow
```javascript
// 显示消息
window.location.href = 'pmsg://{"plugin":"com.test","msgWindow":{"msg":"Hello","c":"§6","b":"2"}}';
```

## 🎯 常见场景

### 场景1: 创建自定义菜单
1. 创建 ZIP 包包含 HTML/CSS/JS
2. 上传到服务器
3. 服务端发送注册消息
4. 客户端自动下载并缓存
5. 玩家通过命令或按钮打开

### 场景2: 实时通知
1. 服务端触发事件
2. 发送 wint 消息
3. 客户端显示 Windows 通知
4. 如果不支持则降级为聊天消息

### 场景3: 动态界面更新
1. 修改 HTML 文件
2. 更新 hash 值
3. 客户端检测到 hash 变化
4. 自动重新下载
5. 刷新界面

## ⚙️ 配置选项

### 修改服务器端口
编辑 `MGuiLocalServer.java`:
```java
private final int port = 27890; // 改为其他端口
```

### 调整加载策略
编辑 `MGuiResourceManager.java`:
```java
if (playerCount < 15) {  // 修改阈值
    // 全量下载
} else {
    // 按需下载
}
```

## 🐛 故障排除

### 问题1: 端口被占用
**症状**: 服务器启动失败
**解决**: 
```bash
# 查看端口占用
netstat -ano | findstr 27890

# 杀死进程或使用其他端口
```

### 问题2: 资源下载失败
**症状**: 控制台显示下载错误
**检查**:
- 本地服务器是否正常运行
- 文件路径是否正确
- 网络连接是否正常

### 问题3: GUI 不显示
**症状**: 命令执行但无界面
**检查**:
- 资源是否已下载
- HTML 文件是否存在
- 查看日志输出

## 📚 学习资源

- [MGUI_README.md](MGUI_README.md) - 完整文档
- [MGUI_IMPLEMENTATION.md](MGUI_IMPLEMENTATION.md) - 实现细节
- Fabric API 文档: https://fabricmc.net/wiki/documentation

## 💡 提示

1. **开发时启用调试日志**
   ```java
   MGuiLogger.debug("调试信息");
   ```

2. **测试前清理缓存**
   ```
   删除 mc/1.21.8/mgui/ 目录
   ```

3. **使用浏览器测试 HTML**
   - 先在浏览器中预览 HTML
   - 确保没有 JavaScript 错误

4. **监控网络请求**
   - 查看控制台日志
   - 确认资源正确加载

---

**祝你开发愉快！** 🎉

如有问题，请查看日志输出或参考完整文档。
