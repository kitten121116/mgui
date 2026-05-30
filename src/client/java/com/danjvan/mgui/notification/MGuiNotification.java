package com.danjvan.mgui.notification;

import com.danjvan.mgui.util.MGuiLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.awt.*;

public class MGuiNotification {
    
    /**
     * 显示系统托盘通知
     * @param title   通知标题
     * @param message 通知正文
     */
    public static void showNotification(String title, String message) {
        MGuiLogger.info("[MGUI 通知] 尝试显示通知: " + title + " - " + message);

        // 检查是否在游戏环境中
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            MGuiLogger.warn("[MGUI 通知] 玩家未就绪，跳过通知");
            return;
        }

        // 尝试 AWT 托盘通知
        if (SystemTray.isSupported()) {
            MGuiLogger.info("[MGUI 通知] 系统支持托盘，正在初始化 AWT 通知...");
            try {
                // 直接在当前线程执行，Minecraft 的渲染循环允许 AWT 调用
                SystemTray tray = SystemTray.getSystemTray();
                Image icon = Toolkit.getDefaultToolkit().getImage(
                    MGuiNotification.class.getResource("/assets/template/icon.png")
                );
                if (icon == null || icon.getWidth(null) <= 0) {
                    icon = Toolkit.getDefaultToolkit().getImage("");
                }

                TrayIcon trayIcon = new TrayIcon(icon, "MGUI");
                trayIcon.setImageAutoSize(true);

                // 添加到系统托盘并显示通知
                tray.add(trayIcon);
                trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
                MGuiLogger.info("[MGUI 通知] 系统托盘通知已发送");

                // 5 秒后清理图标
                new Thread(() -> {
                    try { Thread.sleep(5000); } catch (InterruptedException e) {}
                    tray.remove(trayIcon);
                }).start();
                
                return; // AWT 通知成功，结束方法
                
            } catch (Exception e) {
                MGuiLogger.error("[MGUI 通知] AWT 异常: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            MGuiLogger.warn("[MGUI 通知] 系统不支持托盘，使用游戏内提示");
        }

        // 降级方案：使用 Minecraft 原生提示
        fallbackToMinecraft(client, title, message);
    }
    
    /**
     * 游戏内提示降级方案
     */
    private static void fallbackToMinecraft(Minecraft client, String title, String message) {
        try {
            if (client.player == null) {
                MGuiLogger.warn("[MGUI 通知] 降级失败：玩家对象为空");
                return;
            }

            // 1. 发送聊天消息
            client.player.displayClientMessage(
                Component.literal("§6§l[" + title + "] §e" + message), 
                false
            );
            MGuiLogger.info("[MGUI 通知] 已发送聊天消息");

            // 2. 发送 Title (屏幕中央大字)
            if (client.gui != null) {
                client.gui.setTimes(20, 60, 20); // 淡入, 显示, 淡出 (ticks)
                client.gui.setTitle(Component.literal("§e" + title));
                client.gui.setSubtitle(Component.literal("§f" + message));
                MGuiLogger.info("[MGUI 通知] 已发送 Title 提示");
            }
        } catch (Exception e) {
            MGuiLogger.error("[MGUI 通知] 游戏内提示异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
