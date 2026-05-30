package com.danjvan.mgui.command;

import com.danjvan.mgui.gui.MGuiScreen;
import com.danjvan.mgui.util.MGuiLogger;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class MGuiTestCommand {
    
    public static void register() {
        // TODO: 实现 Fabric API 1.21.8 的命令注册
        MGuiLogger.info("MGUI 测试命令已注册（待实现）");
    }
    
    private static int execute(CommandContext<FabricClientCommandSource> context) {
        // 打开测试 GUI
        MGuiScreen.openGui("com.test.plugin.1");
        return Command.SINGLE_SUCCESS;
    }
}
