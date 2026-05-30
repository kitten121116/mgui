package com.danjvan.mgui.command;

import com.danjvan.mgui.gui.MGuiScreen;
import com.danjvan.mgui.network.MGuiNetworkManager;
import com.danjvan.mgui.notification.MGuiNotification;
import com.danjvan.mgui.util.MGuiLogger;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MGuiApiCommand {
    
    /**
     * 注册 /mguiapi 调试命令
     */
    public static void register() {
        // TODO: Fabric API 1.21.8 命令注册方式可能需要调整
        // 暂时使用日志记录代替
        MGuiLogger.info("/mguiapi 调试命令已注册（待实现完整命令注册）");
        
        // 子命令功能说明：
        // testnotify <消息> - 测试通知功能
        // opengui <pluginId> - 测试打开 GUI
        // sendmsg <json> - 测试发送网络消息
        // info - 显示调试信息
        // execmd <命令> - 测试执行命令
        // reload <pluginId> - 重新加载插件资源
        // help - 显示帮助
    }
    
    /**
     * 测试通知功能：/mguiapi testnotify <消息>
     */
    private static int testNotify(CommandContext<FabricClientCommandSource> context) {
        String message = StringArgumentType.getString(context, "message");
        
        MGuiNotification.showNotification("debug", message);
        sendMessage(context, "§a已发送测试通知: §e" + message);
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * 测试 GUI 打开：/mguiapi opengui <pluginId>
     */
    private static int openGui(CommandContext<FabricClientCommandSource> context) {
        String pluginId = StringArgumentType.getString(context, "pluginId");
        
        MGuiScreen.openGui(pluginId);
        sendMessage(context, "§a尝试打开 GUI: §e" + pluginId);
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * 测试网络消息发送：/mguiapi sendmsg <json>
     */
    private static int sendMessage(CommandContext<FabricClientCommandSource> context) {
        String json = StringArgumentType.getString(context, "json");
        
        MGuiNetworkManager.sendMessageToServer(json);
        sendMessage(context, "§a已发送消息: §e" + json);
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * 查看服务器信息：/mguiapi info
     */
    private static int showInfo(CommandContext<FabricClientCommandSource> context) {
        Minecraft client = context.getSource().getClient();
        
        sendMessage(context, "§a=== MGUI 调试信息 ===");
        
        // 客户端信息
        sendMessage(context, "§eMinecraft 版本: §f" + "1.21.8");
        sendMessage(context, "§eMGUI 版本: §f1.0.0");
        
        // 服务器信息
        if (client.getCurrentServer() != null) {
            sendMessage(context, "§e服务器地址: §f" + client.getCurrentServer().ip);
        } else {
            sendMessage(context, "§e服务器地址: §c单人模式");
        }
        
        // 玩家信息
        if (client.player != null) {
            sendMessage(context, "§e玩家名称: §f" + client.player.getName().getString());
            sendMessage(context, "§e玩家位置: §f" + String.format("%.2f, %.2f, %.2f",
                client.player.getX(), client.player.getY(), client.player.getZ()));
        }
        
        // 世界信息
        if (client.level != null) {
            sendMessage(context, "§e世界类型: §f" + client.level.dimensionType().toString());
            sendMessage(context, "§e在线玩家: §f" + client.level.players().size());
        }
        
        sendMessage(context, "§a=====================");
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * 测试命令执行：/mguiapi execmd <命令>
     */
    private static int executeCommand(CommandContext<FabricClientCommandSource> context) {
        String command = StringArgumentType.getString(context, "command");
        
        MGuiCommandExecutor.executeCommand(command);
        sendMessage(context, "§a已执行命令: §e/" + command);
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * 重新加载资源：/mguiapi reload <pluginId>
     */
    private static int reloadResource(CommandContext<FabricClientCommandSource> context) {
        String pluginId = StringArgumentType.getString(context, "pluginId");
        
        com.danjvan.mgui.resource.MGuiResourceManager.getInstance().requestResourcePack(pluginId);
        sendMessage(context, "§a开始重新加载资源: §e" + pluginId);
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * 显示帮助：/mguiapi help
     */
    private static int showHelp(CommandContext<FabricClientCommandSource> context) {
        sendMessage(context, "§a=== MGUI API 调试命令 ===");
        sendMessage(context, "§e/mguiapi testnotify <消息> §7- 测试通知功能");
        sendMessage(context, "§e/mguiapi opengui <pluginId> §7- 测试打开 GUI");
        sendMessage(context, "§e/mguiapi sendmsg <json> §7- 测试发送网络消息");
        sendMessage(context, "§e/mguiapi execmd <命令> §7- 测试执行命令");
        sendMessage(context, "§e/mguiapi reload <pluginId> §7- 重新加载插件资源");
        sendMessage(context, "§e/mguiapi info §7- 显示调试信息");
        sendMessage(context, "§e/mguiapi help §7- 显示此帮助");
        sendMessage(context, "§a=======================");
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * 发送消息给玩家
     */
    private static void sendMessage(CommandContext<FabricClientCommandSource> context, String message) {
        Minecraft client = context.getSource().getClient();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(message), false);
        }
    }
}

