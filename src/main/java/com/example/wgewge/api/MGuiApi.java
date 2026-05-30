package com.example.wgewge.api;

import com.example.wgewge.network.MGuiPacket;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MGUI API - 供业务mod使用
 * 
 * 业务mod可以通过此API注册UI资源包到服务器
 */
public class MGuiApi {
    private static final Logger LOGGER = LoggerFactory.getLogger("mgui_api");
    
    /**
     * 注册UI资源包（服务端调用）
     * 业务mod需要先解压资源包到服务器的 mguiregs/ 目录
     * 
     * @param player 玩家（用于发送网络消息）
     * @param command 触发UI的命令（例如 "mygui:open"）
     * @param zipFileName ZIP文件名（位于 mguiregs/ 目录下）
     * @param alias 资源包别名（简称，方便区分）
     * @param description UI的描述信息
     */
    public static void registerUi(ServerPlayer player, String command, String zipFileName, 
                                  String alias, String description) {
        JsonObject json = new JsonObject();
        json.addProperty("command", command);
        json.addProperty("zipFileName", zipFileName);
        json.addProperty("alias", alias);
        json.addProperty("description", description != null ? description : "");
        
        if (ServerPlayNetworking.canSend(player, MGuiPacket.UiRegisterPacket.TYPE)) {
            ServerPlayNetworking.send(
                player,
                new MGuiPacket.UiRegisterPacket(json.toString())
            );
            LOGGER.info("UI注册请求已发送: command={}, alias={}, zip={}", command, alias, zipFileName);
        } else {
            LOGGER.warn("无法发送UI注册请求：通道未就绪");
        }
    }
    
    /**
     * 为玩家打开指定UI（服务端调用）
     * 
     * @param player 目标玩家
     * @param command UI对应的命令
     */
    public static void openUiForPlayer(ServerPlayer player, String command) {
        // 这会调用服务器端的openUiForPlayer方法
        com.example.wgewge.server.MGuiServerMod.openUiForPlayer(player, command);
    }
}
