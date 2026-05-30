package com.example.wgewge.client.network;

import com.example.wgewge.network.MGuiPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MGUI 网络处理器
 * 处理客户端与服务器之间的通信
 */
public class MGuiNetworkHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MGuiNetworkHandler.class);
    
    /**
     * 发送消息到服务器
     */
    public static void sendMessageToServer(String messageType, String data) {
        if (!ClientPlayNetworking.canSend(MGuiPacket.C2SPacket.TYPE)) {
            LOGGER.warn("无法发送消息到服务器：通道未就绪");
            return;
        }
        
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", messageType);
        json.addProperty("data", data);
        
        ClientPlayNetworking.send(new MGuiPacket.C2SPacket(json.toString()));
        
        LOGGER.debug("消息已发送到服务器: {}", messageType);
    }
    
    /**
     * 发送截图就绪通知
     */
    public static void sendScreenshotReady() {
        sendMessageToServer("screenshot_ready", "");
    }
    
    /**
     * 发送UI关闭通知
     */
    public static void sendUiClosed(String uiId) {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "ui_closed");
        json.addProperty("uiId", uiId);
        
        ClientPlayNetworking.send(new MGuiPacket.ClientRequestPacket(json.toString()));
    }
}
