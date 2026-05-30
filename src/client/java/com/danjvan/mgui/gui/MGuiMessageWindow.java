package com.danjvan.mgui.gui;

import com.danjvan.mgui.util.MGuiLogger;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class MGuiMessageWindow {
    
    /**
     * 显示消息窗口（msgWindow 参数）
     */
    public static void showMessage(String message, JsonObject params) {
        Minecraft client = Minecraft.getInstance();
        
        if (client.player != null) {
            // 解析颜色
            String color = params.has("c") ? params.get("c").getAsString() : "§f";
            // 解析大小
            String size = params.has("b") ? params.get("b").getAsString() : "1";
            
            String formattedMessage = color + message;
            
            client.gui.getChat().addMessage(Component.literal(formattedMessage));
            MGuiLogger.info("显示消息: " + formattedMessage);
        }
    }
}
