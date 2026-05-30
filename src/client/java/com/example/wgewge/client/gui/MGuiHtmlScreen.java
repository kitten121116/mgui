package com.example.wgewge.client.gui;

import com.example.wgewge.MGuiConstants;
import com.example.wgewge.client.js.JsBridge;
import com.example.wgewge.client.resource.MGuiResourcePackManager;
import com.example.wgewge.client.webview.MGuiWebViewManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * MGUI HTML 屏幕
 * 负责渲染HTML界面并提供JS交互桥接
 */
public class MGuiHtmlScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(MGuiHtmlScreen.class);
    
    private final String uiId;
    private final String command;
    private JsBridge jsBridge;
    private String htmlPath;
    private boolean isInitialized = false;
    
    protected MGuiHtmlScreen(String uiId, String command) {
        super(Component.literal("MGUI - " + uiId));
        this.uiId = uiId;
        this.command = command;
    }
    
    @Override
    protected void init() {
        if (!isInitialized) {
            initializeUi();
            isInitialized = true;
        }
    }
    
    /**
     * 初始化UI
     */
    private void initializeUi() {
        LOGGER.info("初始化UI: {}", uiId);
        
        // 获取HTML文件路径
        htmlPath = MGuiResourcePackManager.getInstance().getHtmlPath(uiId);
        File htmlFile = new File(htmlPath);
        
        if (!htmlFile.exists()) {
            LOGGER.error("HTML文件不存在: {}", htmlPath);
            return;
        }
        
        // 初始化JS桥接
        jsBridge = new JsBridge(this.minecraft, this.uiId, this.command);
        
        // 使用 MGuiWebViewManager 打开 UI
        try {
            MGuiWebViewManager webViewManager = MGuiWebViewManager.getInstance();
            webViewManager.openUi(uiId, htmlPath);
            LOGGER.info("UI已通过 GUI.exe 打开");
        } catch (Exception e) {
            LOGGER.error("打开 UI 失败: {}", e.getMessage(), e);
        }
        
        LOGGER.info("UI初始化完成");
    }
    

    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        // 渲染半透明背景
        guiGraphics.fill(0, 0, this.width, this.height, 0x66000000);
        
        // 显示提示信息
        guiGraphics.drawCenteredString(
            this.font,
            "MGUI - " + uiId,
            this.width / 2,
            20,
            0xFFFFFF
        );
        
        guiGraphics.drawCenteredString(
            this.font,
            "浏览器窗口已打开",
            this.width / 2,
            this.height / 2 - 10,
            0xAAAAAA
        );
        
        guiGraphics.drawCenteredString(
            this.font,
            "按 ESC 关闭UI",
            this.width / 2,
            this.height / 2 + 10,
            0xAAAAAA
        );
        
        super.render(guiGraphics, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false; // 不暂停游戏
    }
    
    @Override
    public void onClose() {
        // 关闭 UI 窗口
        try {
            MGuiWebViewManager webViewManager = MGuiWebViewManager.getInstance();
            webViewManager.closeUi(uiId);
        } catch (Exception e) {
            LOGGER.error("关闭 UI 失败: {}", e.getMessage(), e);
        }
        
        // 通知服务器UI已关闭
        if (jsBridge != null) {
            jsBridge.notifyUiClosed();
        }
        super.onClose();
    }
    
    /**
     * 关闭UI（供JS调用）
     */
    public void closeFromJs() {
        Minecraft.getInstance().execute(() -> {
            this.onClose();
        });
    }
    
    /**
     * 获取JS桥接对象
     */
    public JsBridge getJsBridge() {
        return jsBridge;
    }
    
    /**
     * 获取UI ID
     */
    public String getUiId() {
        return uiId;
    }
    
    /**
     * 打开屏幕
     */
    public static void openScreen(String uiId, String command) {
        Minecraft client = Minecraft.getInstance();
        MGuiHtmlScreen screen = new MGuiHtmlScreen(uiId, command);
        client.setScreen(screen);
    }
}