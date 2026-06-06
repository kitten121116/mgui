pluginManagement {
	repositories {
		maven {
			name = 'Fabric'
			url = 'https://maven.aliyun.com/repository/public'
		}
		maven {
			name = 'MavenCentral'
			url = 'https://maven.aliyun.com/repository/central'
		}
		maven {
			name = 'GradlePlugins'
			url = 'https://maven.aliyun.com/repository/gradle-plugin'
		}
		maven {
			name = 'FabricBackup'
			url = 'https://maven.fabricmc.net/'
		}
		mavenCentral()
		gradlePluginPortal()
	}
}

rootProject.name = 'template'
# MGUI - Minecraft GUI Mod

MGUI 是一个用于在 Minecraft 中显示网页 UI 的 Mod/插件系统。支持 Fabric 服务器和 Paper/Bukkit 插件两种部署方式。

## 功能特性

- ✅ 支持在游戏中显示网页 UI
- ✅ 支持多种分辨率模式（全屏、自定义、预设）
- ✅ 支持 TCP 协议通信（用于 Paper 服务器）
- ✅ 支持 FRP 端口穿透配置
- ✅ 双协议支持（Fabric 和 Paper）

## 部署方式

### 1. Fabric Mod（客户端和服务器）

将 `template-1.0.0.jar` 放入客户端和服务器的 `mods` 文件夹中。

### 2. Paper/Bukkit 插件（仅服务器）

将 `Permguiplg-1.0.0.jar` 放入服务器的 `plugins` 文件夹中。

**注意**：使用 Paper 插件时，客户端仍需安装 Fabric Mod。

## 命令文档

### Fabric 服务器命令

#### `/mgui_open <url> <resolution> <width> <height>`

在玩家屏幕上打开一个网页 UI。

**参数说明**：
| 参数 | 类型 | 说明 |
|------|------|------|
| url | String | 网页地址（必须以 http:// 或 https:// 开头） |
| resolution | int | 分辨率模式：1=全屏，2=自定义大小，3=正常预设 |
| width | int | 窗口宽度（分辨率为2时生效） |
| height | int | 窗口高度（分辨率为2时生效） |

**权限要求**：`mgui.command.open`

**示例**：
```
/mgui_open http://example.com 2 800 600
/mgui_open https://bilibili.com 1 0 0
/mgui_open https://google.com 3 0 0
```

#### `/mgui_close`

关闭当前打开的 UI。

**权限要求**：`mgui.command.close`

#### `/mgui_reload`

重新加载 MGUI 配置文件。

**权限要求**：`mgui.command.reload`（需要 OP）

#### `/mgui_version`

显示 MGUI 版本信息。

**权限要求**：无

---

### Paper/Bukkit 插件命令

#### `/mgui_open2 <player> <resolution> <width> <height> <url>`

打开指定玩家的网页 UI。

**参数说明**：
| 参数 | 类型 | 说明 |
|------|------|------|
| player | String | 目标玩家名称 |
| resolution | int | 分辨率模式：1=全屏，2=自定义大小，3=正常预设 |
| width | int | 窗口宽度（分辨率为2时生效） |
| height | int | 窗口高度（分辨率为2时生效） |
| url | String | 网页地址 |

**权限要求**：`mgui.command.open`

**示例**：
```
/mgui_open2 Steve 2 800 600 http://example.com
/mgui_open2 Alex 1 0 0 https://bilibili.com
```

#### `/mgui_close2 <player>`

关闭指定玩家的 UI。

**权限要求**：`mgui.command.close`

**示例**：
```
/mgui_close2 Steve
```

#### `/mgui_frp set <host> <port>`

设置 FRP 外部访问地址和端口（用于端口穿透）。

**参数说明**：
| 参数 | 类型 | 说明 |
|------|------|------|
| host | String | FRP 服务端地址 |
| port | int | FRP 映射后的端口 |

**权限要求**：`mgui.command.frp`（需要 OP）

**示例**：
```
/mgui_frp set cd.4.frp.one 39317
```

#### `/mgui_frp clear`

清除 FRP 配置，使用默认端口。

**权限要求**：`mgui.command.frp`（需要 OP）

#### `/mgui_frp info`

查看当前 FRP 配置信息。

**权限要求**：`mgui.command.frp`（需要 OP）

#### `/mgui_tcp_info`

**隐藏指令** - 客户端自动发送，获取 TCP 服务器地址。

**使用说明**：此命令由客户端 Mod 自动发送，玩家无需手动执行。

---

### 分辨率模式说明

| 模式 | 值 | 说明 |
|------|-----|------|
| 全屏 | 1 | 占据整个游戏窗口，忽略 width 和 height 参数 |
| 自定义 | 2 | 使用指定的 width 和 height |
| 预设 | 3 | 使用默认预设大小，忽略 width 和 height 参数 |

---

## 权限节点

### Fabric 服务器

| 权限节点 | 说明 | 默认权限 |
|----------|------|----------|
| mgui.command.open | 打开 UI | OP |
| mgui.command.close | 关闭 UI | OP |
| mgui.command.reload | 重载配置 | OP |
| mgui.command.version | 查看版本 | 所有人 |

### Paper/Bukkit 插件

| 权限节点 | 说明 | 默认权限 |
|----------|------|----------|
| mgui.command.open | 打开 UI | OP |
| mgui.command.close | 关闭 UI | OP |
| mgui.command.frp | FRP 配置 | OP |

---

## 配置文件

### Paper/Bukkit 插件配置 (`plugins/Permguiplg/config.yml`)

```yaml
# TCP 服务器配置
tcp:
  # 是否启用 TCP 服务器
  enabled: true
  # TCP 监听端口
  port: 26698

# FRP 端口穿透配置
frp:
  # 外部访问地址（FRP 服务端地址）
  external-host: ""
  # 外部访问端口（FRP 映射后的端口）
  external-port: 26698

# UI 配置
ui:
  # UI 打开冷却时间（毫秒）
  cooldown: 5000
```

---

## 通信流程

### Fabric 服务器
1. 客户端连接 → 发送握手消息 → 服务器验证
2. 玩家执行 `/mgui_open` → 服务器发送 UI 消息 → 客户端打开 UI

### Paper 服务器
1. 客户端连接 → 发送握手消息 → 服务器验证 Mod 状态
2. 客户端请求 TCP 地址 → 服务器返回 FRP 地址
3. 客户端建立 TCP 连接 → 完成二次验证
4. 玩家执行 `/mgui_open2` → 服务器通过聊天消息发送 UI 指令 → 客户端打开 UI

---

## 技术说明

### 协议兼容性

- **Fabric ↔ Fabric**：使用 Fabric CustomPacketPayload
- **Fabric ↔ Paper**：使用聊天消息作为备用通道

### TCP 服务器

Paper 插件内置 TCP 服务器，用于：
- 验证客户端 Mod 状态
- 支持 FRP 端口穿透
- 提供可靠的通信通道

---

## 许可证

MIT License

## 支持

如有问题，请提交 Issue 或联系开发者。