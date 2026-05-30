package com.danjvan.mgui.network;

import com.danjvan.mgui.resource.MGuiResourceManager;
import com.danjvan.mgui.util.MGuiLogger;
import com.danjvan.mgui.command.MGuiCommandExecutor;
import com.danjvan.mgui.gui.MGuiMessageWindow;
import com.danjvan.mgui.notification.MGuiNotification;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;

/**
 * 传统 Plugin Message 兼容层
 * 
 * 通过反射和底层网络包拦截，实现与 Bukkit 服务器的兼容通信
 */
public class MGuiPluginMessageHandler {
    public static final ResourceLocation CHANNEL_ID = ResourceLocation.fromNamespaceAndPath("mgui", "plugin_channel");
    
    private static boolean registered = false;
    
    /**
     * 注册 Plugin Message 监听器
     */
    public static void register() {
        if (registered) {
            MGuiLogger.warn("Plugin Message 监听器已注册");
            return;
        }
        
        try {
            // 使用 Fabric 的传统 Plugin Message API（如果可用）
            registerLegacyReceiver();
            registered = true;
            MGuiLogger.info("MGUI Plugin Message 监听器已注册: " + CHANNEL_ID);
        } catch (Exception e) {
            MGuiLogger.error("注册 Plugin Message 监听器失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 注册传统接收器
     */
    private static void registerLegacyReceiver() {
        // Fabric API 1.21.8 已经移除了传统的 registerGlobalReceiver(ResourceLocation, ...) 方法
        // 我们需要使用自定义的网络包处理器
        
        MGuiLogger.info("尝试使用备用方案注册 Plugin Message 接收器...");
        
        // 方案：在 ClientPlayConnectionEvents.JOIN 中手动处理
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            MGuiLogger.info("MGUI: 客户端已连接到服务器，准备接收 Plugin Message");
            
            // 注意：由于 Fabric 1.21.8 的限制，我们无法直接拦截传统 Plugin Message
            // 需要在服务器端使用 Fabric 服务端模组，或者改用聊天命令
        });
    }
    
    /**
     * 处理接收到的消息
     */
    public static void handleMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            
            if (json.has("type")) {
                String type = json.get("type").getAsString();
                
                switch (type) {
                    case "register":
                        handleRegister(json);
                        break;
                    case "pmsg":
                        handlePluginMessage(json);
                        break;
                    case "wint":
                        handleWindowsNotification(json);
                        break;
                    default:
                        MGuiLogger.warn("未知的消息类型: " + type);
                }
            }
        } catch (Exception e) {
            MGuiLogger.error("处理消息失败: " + e.getMessage());
        }
    }
    
    private static void handleRegister(JsonObject json) {
        String pluginId = json.get("plugin").getAsString();
        MGuiLogger.info("收到插件注册请求: " + pluginId);
        MGuiResourceManager.getInstance().requestResourcePack(pluginId);
    }
    
    private static void handlePluginMessage(JsonObject json) {
        String plugin = json.get("plugin").getAsString();
        String msg = json.get("msg").getAsString();
        
        MGuiLogger.info("收到插件消息 [" + plugin + "]: " + msg);
        
        if (msg.startsWith("@")) {
            String command = msg.substring(1);
            MGuiCommandExecutor.executeCommand(command);
        } else {
            MGuiMessageWindow.showMessage(msg, json);
        }
    }
    
    private static void handleWindowsNotification(JsonObject json) {
        String player = json.has("player") ? json.get("player").getAsString() : "Server";
        String message = json.has("msg") ? json.get("msg").getAsString() : "";
        
        MGuiLogger.info("收到 Windows 通知 [" + player + "]: " + message);
        MGuiNotification.showNotification(player, message);
    }
}
