package com.danjvan.mgui.command;

import com.danjvan.mgui.util.MGuiLogger;
import net.minecraft.client.Minecraft;

public class MGuiCommandExecutor {
    
    /**
     * 执行命令（ccmd:// 协议）
     */
    public static void executeCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        
        if (client.player != null) {
            MGuiLogger.info("执行命令: " + command);
            client.player.connection.sendCommand(command);
        }
    }
}
