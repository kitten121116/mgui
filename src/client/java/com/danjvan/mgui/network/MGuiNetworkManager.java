package com.danjvan.mgui.network;

import com.danjvan.mgui.util.MGuiLogger;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * MGUI 网络管理器（简化版）
 * 
 * 由于 Fabric 1.21.8 的 Payload 系统与 Bukkit 不兼容，
 * 我们使用 Mixin 拦截底层网络包来接收 Plugin Message
 */
public class MGuiNetworkManager {
    
    /**
     * 初始化网络管理器
     */
    public static void init() {
        MGuiLogger.info("MGUI 网络管理器正在初始化...");
        
        try {
            // 注册 Plugin Message 处理器（通过 Mixin 拦截）
            MGuiPluginMessageHandler.register();
            
            // 注册客户端连接事件
            ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                MGuiLogger.info("MGUI: 客户端已连接到服务器");
                MGuiLogger.info("MGUI: 等待服务器发送 Plugin Message...");
            });
            
            MGuiLogger.info("MGUI 网络管理器初始化完成（使用 Mixin 拦截模式）");
            
        } catch (Exception e) {
            MGuiLogger.error("MGUI 网络初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 发送消息到服务端（仅用于调试，实际不发送）
     */
    public static void sendMessageToServer(String message) {
        MGuiLogger.warn("sendMessageToServer 已禁用：Bukkit 服务器无法解码 Fabric Payload");
        MGuiLogger.info("模拟发送消息: " + message);
    }
}
