package com.example.wgewge.client.js;

import com.example.wgewge.network.MGuiPacket;
import com.example.wgewge.client.screenshot.ScreenshotManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaScript 桥接类
 * 提供JS可调用的原生功能接口
 */
public class JsBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsBridge.class);
    
    private final Minecraft minecraft;
    private final String uiId;
    private final String command;
    
    public JsBridge(Minecraft minecraft, String uiId, String command) {
        this.minecraft = minecraft;
        this.uiId = uiId;
        this.command = command;
    }
    
    /**
     * 关闭当前UI（供JS调用）
     * JS调用: mgui.close()
     */
    public void close() {
        if (minecraft.screen != null) {
            minecraft.execute(() -> {
                minecraft.setScreen(null);
            });
        }
    }
    
    /**
     * 获取游戏截图（供JS调用）
     * JS调用: mgui.getScreenshot(callback)
     * 
     * @param callback JS回调函数名
     * @return 截图的base64数据或文件路径
     */
    public String getScreenshot(String callback) {
        String screenshotPath = ScreenshotManager.getInstance().getLatestScreenshotPath();
        if (screenshotPath != null) {
            // 返回截图路径，JS可以通过本地服务器访问
            String url = "http://127.0.0.1:27890/screenshot/" + uiId;
            return url;
        } else {
            return "";
        }
    }
    
    /**
     * 执行客户端命令（供JS调用）
     * JS调用: mgui.executeCommand("/say hello")
     * 
     * @param commandStr 要执行的命令（可以带/或不带/）
     */
    public void executeCommand(String commandStr) {
        minecraft.execute(() -> {
            try {
                // 直接使用聊天发送命令
                if (minecraft.player != null) {
                    // 确保命令以 / 开头
                    String cmd = commandStr.startsWith("/") ? commandStr : "/" + commandStr;
                    
                    // 通过聊天发送命令
                    minecraft.player.connection.sendCommand(cmd.substring(1));
                }
            } catch (Exception e) {
                LOGGER.error("命令执行失败: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * 发送消息到聊天栏（供JS调用）
     * JS调用: mgui.sendMessage("Hello!")
     * 
     * @param message 消息内容
     */
    public void sendMessage(String message) {
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(message), 
                    false
                );
            }
        });
    }
    
    /**
     * 通知服务器UI已关闭
     */
    public void notifyUiClosed() {
        if (ClientPlayNetworking.canSend(MGuiPacket.ClientRequestPacket.TYPE)) {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("type", "ui_closed");
            json.addProperty("uiId", uiId);
            
            ClientPlayNetworking.send(new MGuiPacket.ClientRequestPacket(json.toString()));
            
            LOGGER.info("已通知服务器UI关闭: {}", uiId);
        }
    }
    
    /**
     * 获取UI信息（供JS调用）
     * JS调用: mgui.getUiInfo()
     * 
     * @return JSON字符串包含UI信息
     */
    public String getUiInfo() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("uiId", uiId);
        json.addProperty("command", command);
        json.addProperty("playerName", minecraft.player != null ? 
            minecraft.player.getName().getString() : "unknown");
        
        return json.toString();
    }
    
    /**
     * 获取玩家名称
     */
    public String getPlayerName() {
        return minecraft.player != null ? 
            minecraft.player.getName().getString() : "unknown";
    }
    
    /**
     * 获取 UI ID
     */
    public String getUiId() {
        return uiId;
    }
}