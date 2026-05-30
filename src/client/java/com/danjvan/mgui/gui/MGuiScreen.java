package com.danjvan.mgui.gui;

import com.danjvan.mgui.util.MGuiLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class MGuiScreen extends Screen {
    private final String pluginId;
    private final String htmlPath;
    private BufferedImage backgroundImg;
    
    protected MGuiScreen(String pluginId, String htmlPath) {
        super(Component.literal("MGUI - " + pluginId));
        this.pluginId = pluginId;
        this.htmlPath = htmlPath;
    }
    
    @Override
    protected void init() {
        // 加载背景图
        loadBackgroundImage();
        
        // TODO: 解析 HTML 并渲染界面
        MGuiLogger.info("初始化 MGUI 屏幕: " + pluginId);
    }
    
    private void loadBackgroundImage() {
        try {
            // 从本地服务器获取背景图
            String bgUrl = "http://127.0.0.1:27890/bg.png?" + pluginId;
            
            // 这里简化处理，实际应该异步加载
            File bgFile = new File("mc/1.21.8/mgui/gui/" + pluginId + "/bg.png");
            if (bgFile.exists()) {
                backgroundImg = ImageIO.read(bgFile);
                MGuiLogger.info("背景图加载成功");
            }
        } catch (IOException e) {
            MGuiLogger.error("加载背景图失败: " + e.getMessage());
        }
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        // 渲染背景
        if (backgroundImg != null) {
            // TODO: 将 BufferedImage 渲染到屏幕
        }
        
        // 渲染 HTML 内容（需要集成 WebView 或 HTML 渲染引擎）
        // 这里暂时显示占位文本
        
        super.render(guiGraphics, mouseX, mouseY, delta);
        
        // 绘制标题
        guiGraphics.drawCenteredString(
            this.font,
            "MGUI Plugin: " + pluginId,
            this.width / 2,
            20,
            0xFFFFFF
        );
        
        // 绘制提示信息
        guiGraphics.drawCenteredString(
            this.font,
            "HTML 渲染功能待实现",
            this.width / 2,
            this.height / 2,
            0xAAAAAA
        );
    }
    
    @Override
    public boolean isPauseScreen() {
        return false; // 不暂停游戏
    }
    
    /**
     * 打开 MGUI 屏幕
     */
    public static void openGui(String pluginId) {
        Minecraft client = Minecraft.getInstance();
        String htmlPath = "mc/1.21.8/mgui/gui/" + pluginId + "/main.html";
        
        MGuiScreen screen = new MGuiScreen(pluginId, htmlPath);
        client.setScreen(screen);
    }
}
