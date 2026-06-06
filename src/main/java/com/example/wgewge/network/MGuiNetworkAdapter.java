package com.example.wgewge.network;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MGUI 网络适配器
 * 
 * 统一处理两种通信方式：
 * 1. Fabric CustomPacketPayload（纯mod服务器）
 * 2. Plugin Message Channel（Paper插件服务器）
 * 
 * 自动检测服务器类型并选择合适的通信方式
 */
public class MGuiNetworkAdapter {
    
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("mgui_network");
    
    /**
     * 当前使用的通信模式
     */
    private static CommunicationMode currentMode = CommunicationMode.AUTO;
    
    /**
     * 通信模式枚举
     */
    public enum CommunicationMode {
        AUTO,           // 自动检测
        FABRIC_ONLY,    // 仅使用Fabric协议
        PLUGIN_MESSAGE  // 仅使用Plugin Message
    }
    
    /**
     * 设置通信模式
     */
    public static void setCommunicationMode(CommunicationMode mode) {
        currentMode = mode;
        LOGGER.info("MGUI 通信模式已设置为: {}", mode);
    }
    
    /**
     * 获取当前通信模式
     */
    public static CommunicationMode getCommunicationMode() {
        return currentMode;
    }
    
    /**
     * 发送消息给客户端（自动选择通信方式）
     */
    public static void sendToClient(ServerPlayer player, JsonObject message) {
        sendToClient(player, message.toString());
    }
    
    /**
     * 发送消息给客户端（自动选择通信方式）
     */
    public static void sendToClient(ServerPlayer player, String message) {
        switch (currentMode) {
            case FABRIC_ONLY:
                sendViaFabric(player, message);
                break;
            case PLUGIN_MESSAGE:
                sendViaPluginMessage(player, message);
                break;
            case AUTO:
            default:
                // 优先尝试Fabric方式，如果失败则回退到Plugin Message
                try {
                    sendViaFabric(player, message);
                } catch (Exception e) {
                    LOGGER.debug("Fabric通信失败，尝试Plugin Message: {}", e.getMessage());
                    sendViaPluginMessage(player, message);
                }
                break;
        }
    }
    
    /**
     * 使用Fabric CustomPacketPayload发送消息
     */
    private static void sendViaFabric(ServerPlayer player, String message) {
        try {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player, 
                new MGuiPacket.S2CPacket(message)
            );
            LOGGER.debug("通过Fabric协议发送消息: {}", message.length() > 50 ? message.substring(0, 50) + "..." : message);
        } catch (NoClassDefFoundError | Exception e) {
            throw new RuntimeException("Fabric通信失败", e);
        }
    }
    
    /**
     * 使用Plugin Message Channel发送消息（Paper兼容）
     */
    private static void sendViaPluginMessage(ServerPlayer player, String message) {
        try {
            // 使用反射调用Bukkit API（避免直接依赖Bukkit）
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Object craftPlayer = craftPlayerClass.cast(player);
            
            // 获取Bukkit Player对象
            Class<?> playerClass = Class.forName("org.bukkit.entity.Player");
            Object bukkitPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
            
            // 调用sendPluginMessage方法
            Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin");
            Object plugin = getPluginInstance();
            
            if (plugin != null) {
                byte[] data = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                playerClass.getMethod("sendPluginMessage", pluginClass, String.class, byte[].class)
                    .invoke(bukkitPlayer, plugin, "mgui:main", data);
                
                LOGGER.debug("通过Plugin Message发送消息: {}", message.length() > 50 ? message.substring(0, 50) + "..." : message);
            }
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Bukkit API不可用，无法使用Plugin Message");
            throw new RuntimeException("Plugin Message通信失败", e);
        } catch (Exception e) {
            LOGGER.error("Plugin Message发送失败: {}", e.getMessage());
            throw new RuntimeException("Plugin Message通信失败", e);
        }
    }
    
    /**
     * 获取插件实例（通过反射）
     */
    private static Object getPluginInstance() {
        try {
            Class<?> pluginManagerClass = Class.forName("org.bukkit.Bukkit");
            Object pluginManager = pluginManagerClass.getMethod("getPluginManager").invoke(null);
            Class<?> pluginManagerImplClass = pluginManager.getClass();
            Object plugin = pluginManagerImplClass.getMethod("getPlugin", String.class)
                .invoke(pluginManager, "mgui");
            return plugin;
        } catch (Exception e) {
            LOGGER.debug("无法获取MGUI插件实例: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 发送ZIP数据块给客户端
     */
    public static void sendZipChunk(ServerPlayer player, int chunkIndex, int totalChunks, byte[] chunkData) {
        String base64Data = java.util.Base64.getEncoder().encodeToString(chunkData);
        String message = MGuiProtocol.createZipChunkMessage(chunkIndex, totalChunks, base64Data);
        sendToClient(player, message);
    }
    
    /**
     * 发送打开UI指令（URL模式）
     */
    public static void sendOpenUiDirect(ServerPlayer player, String url, String resolution, String width, String height) {
        String message = MGuiProtocol.createOpenUiDirectMessage(url, resolution, width, height);
        sendToClient(player, message);
    }
    
    /**
     * 发送打开UI指令（ZIP模式）
     */
    public static void sendOpenUi(ServerPlayer player, String alias, String command, String description, String md5) {
        String message = MGuiProtocol.createOpenUiMessage(alias, command, description, md5);
        sendToClient(player, message);
    }
    
    /**
     * 发送关闭UI指令
     */
    public static void sendCloseUi(ServerPlayer player) {
        String message = MGuiProtocol.createCloseUiMessage();
        sendToClient(player, message);
    }
    
    /**
     * 发送ZIP传输完成指令
     */
    public static void sendZipComplete(ServerPlayer player) {
        String message = MGuiProtocol.createZipCompleteMessage();
        sendToClient(player, message);
    }
}