# MGUI - Minecraft GUI 系统

## 简介
这是一个基于 Fabric 的 Minecraft 自定义 GUI 系统，支持通过插件通讯通道实现动态界面渲染。

## 功能特性

### 1. 网络通信
- ✅ 插件注册流程（SFV）
- ✅ 自定义消息协议（pmsg://）
- ✅ 命令执行协议（ccmd://）
- ✅ Windows 通知支持（wint）

### 2. 资源管理
- ✅ 自动下载和缓存资源包
- ✅ Hash 校验确保资源一致性
- ✅ 按需加载（根据玩家数量）
- ✅ ZIP 文件解压

### 3. 本地服务器
- ✅ HTTP 服务器（端口 27890）
- ✅ 背景图服务
- ✅ 资源文件访问

### 4. GUI 渲染
- ⚠️ HTML 渲染（基础框架已实现，需要集成 WebView）
- ✅ 背景图加载
- ✅ 屏幕界面管理

## 项目结构

```
src/client/java/com/example/wgewge/mgui/
├── network/
│   └── MGuiNetworkManager.java      # 网络通信管理器
├── resource/
│   └── MGuiResourceManager.java     # 资源管理器
├── server/
│   └── MGuiLocalServer.java         # 本地 HTTP 服务器
├── gui/
│   ├── MGuiScreen.java              # GUI 屏幕
│   └── MGuiMessageWindow.java       # 消息窗口
├── command/
│   └── MGuiCommandExecutor.java     # 命令执行器
├── notification/
│   └── MGuiNotification.java        # 通知系统
└── util/
    └── MGuiLogger.java              # 日志工具
```

## 使用流程

### ① 注册流程（SFV）
1. 业务插件发送注册请求到兼容插件
2. 兼容插件生成 res.json
3. 客户端拉取资源列表

### ② 拉取与展示（CCIL）
1. 客户端请求 res.json
2. 根据玩家数量决定下载策略
3. 请求 pak.json 并校验 hash
4. 下载或更新资源包

### ③ 渲染流程
1. 读取 main.html
2. 生成背景图快照（bg.png）
3. 挂载到本地服务器
4. 渲染界面

## 协议说明

### pmsg:// 协议
```json
{
  "plugin": "com.example.test.1",
  "msg": "@tp"
}
```

### ccmd:// 协议
```
ccmd://tp @a 0 0 0
```

### msgWindow 参数
```json
{
  "msgWindow": {
    "msg": "欢迎消息",
    "c": "§6",
    "b": "2"
  }
}
```

### wint 通知
```
wint("player$playername", "这是一条通知")
```

## 目录结构

```
/mc/1.21.8/mgui/{服务器地址}/
├── pak.json          # 包信息（每次拉取）
├── res.json          # 资源列表（每次拉取）
├── pak/              # ZIP 原文件
└── gui/              # 已解压文件
    └── com.xxx.x.1/
        ├── main.html
        ├── res/
        └── md.json
```

## TODO

### 高优先级
- [ ] 集成 HTML 渲染引擎（建议使用 JavaFX WebView 或 JCEF）
- [ ] 完善背景图生成逻辑
- [ ] 添加资源预加载机制
- [ ] 优化异步下载性能

### 中优先级
- [ ] 支持 CSS 样式
- [ ] 支持 JavaScript 交互
- [ ] 添加 GUI 动画效果
- [ ] 实现缓存清理功能

### 低优先级
- [ ] 支持多主题切换
- [ ] 添加 GUI 编辑器
- [ ] 支持插件热重载
- [ ] 性能监控和优化

## 编译和运行

```bash
# 编译模组
./gradlew build

# 运行客户端
./gradlew runClient
```

## 注意事项

1. **端口占用**：MGUI 使用端口 27890，确保该端口未被占用
2. **Java AWT**：Windows 通知功能需要 Java AWT 支持
3. **HTML 渲染**：当前版本仅实现基础框架，完整的 HTML 渲染需要额外集成
4. **网络权限**：需要允许本地网络连接

## 依赖

- Fabric API
- Minecraft 1.21.8
- Java 21+

## 许可证

本项目遵循 MIT 许可证
