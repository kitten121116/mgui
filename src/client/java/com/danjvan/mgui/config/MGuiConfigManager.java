package com.danjvan.mgui.config;

import com.danjvan.mgui.util.MGuiLogger;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;

/**
 * MGUI 配置管理器 - 强制启用 AWT 系统托盘支持
 */
public class MGuiConfigManager {
    
    /**
     * 初始化并强制启用 AWT
     */
    public static void init() {
        MGuiLogger.info("[配置管理] 正在强制启用 AWT 系统托盘...");
        
        // 尝试绕过 headless 限制
        forceEnableAWT();
        
        // 验证是否成功
        if (java.awt.SystemTray.isSupported()) {
            MGuiLogger.info("[配置管理] ✓ AWT 系统托盘已成功启用");
        } else {
            MGuiLogger.warn("[配置管理] ⚠ AWT 系统托盘仍不支持");
            MGuiLogger.warn("[配置管理] 请在 Minecraft 启动器中添加 JVM 参数:");
            MGuiLogger.warn("[配置管理]    -Djava.awt.headless=false");
        }
    }
    
    /**
     * 强制启用 AWT（通过反射修改系统属性）
     */
    private static void forceEnableAWT() {
        try {
            // 方法 1：尝试修改系统属性
            System.setProperty("java.awt.headless", "false");
            MGuiLogger.debug("[配置管理] 已设置 java.awt.headless=false");
            
            // 方法 2：尝试通过反射修改 Toolkit 的 headless 状态
            try {
                Class<?> toolkitClass = Class.forName("java.awt.Toolkit");
                Field headlessField = toolkitClass.getDeclaredField("headless");
                headlessField.setAccessible(true);
                headlessField.setBoolean(null, false);
                MGuiLogger.debug("[配置管理] 已通过反射修改 Toolkit.headless");
            } catch (Exception e) {
                MGuiLogger.debug("[配置管理] 反射修改失败: " + e.getMessage());
            }
            
            // 方法 3：强制初始化 AWT 相关类
            Class.forName("java.awt.SystemTray");
            Class.forName("java.awt.TrayIcon");
            Class.forName("javax.swing.ImageIcon");
            MGuiLogger.debug("[配置管理] AWT 类加载完成");
            
        } catch (Exception e) {
            MGuiLogger.error("[配置管理] 启用 AWT 失败: " + e.getMessage());
        }
    }
}
