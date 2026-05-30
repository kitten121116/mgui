package com.example.wgewge.network;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MGUI 网络适配器
 * 支持 Fabric CustomPacketPayload 协议
 */
public class MGuiNetworkAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MGuiNetworkAdapter.class);
    
    /**
     * 发送消息到客户端
     */
    public static void sendToClient(ServerPlayer player, JsonObject message) {
        String jsonData = message.toString();
        
        // 使用 Fabric CustomPacketPayload 发送
        sendViaCustomPacket(player, jsonData);
    }
    
    /**
     * 通过 CustomPacketPayload 发送（Fabric 原生）
     */
    private static void sendViaCustomPacket(ServerPlayer player, String data) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
            player, 
            new MGuiPacket.S2CPacket(data)
        );
        LOGGER.debug("使用 Fabric CustomPacketPayload 发送数据");
    }
}