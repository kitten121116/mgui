package com.example.wgewge.client.screenshot;

import com.example.wgewge.MGuiConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

/**
 * 截图管理器
 * 负责截取游戏画面并保存到缓存
 */
public class ScreenshotManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScreenshotManager.class);
    private static ScreenshotManager instance;
    
    private String latestScreenshotPath;
    private boolean isCapturing = false;
    
    private ScreenshotManager() {}
    
    public static synchronized ScreenshotManager getInstance() {
        if (instance == null) {
            instance = new ScreenshotManager();
        }
        return instance;
    }
    
    /**
     * 初始化
     */
    public void init() {
        try {
            // 创建截图缓存目录
            Path screenshotDir = Paths.get(MGuiConstants.SCREENSHOT_CACHE_PATH);
            Files.createDirectories(screenshotDir);
            LOGGER.info("截图管理器初始化完成: {}", screenshotDir.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("创建截图目录失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 截取游戏画面
     * 
     * @param onComplete 完成后的回调
     */
    public void captureScreenshot(Runnable onComplete) {
        if (isCapturing) {
            LOGGER.warn("正在截图中，跳过本次请求");
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        
        isCapturing = true;
        
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            try {
                // 使用RenderSystem截取屏幕
                int width = client.getWindow().getWidth();
                int height = client.getWindow().getHeight();
                
                // 创建NativeImage来存储截图
                NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
                
                // 绑定帧缓冲区并读取像素
                org.lwjgl.opengl.GL11.glReadPixels(0, 0, width, height, 
                    org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, 
                    image.getPixels());
                
                // 转换为BufferedImage
                BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        int pixel = image.getPixel(x, y);
                        // RGBA转ABGR
                        int r = (pixel >> 0) & 0xFF;
                        int g = (pixel >> 8) & 0xFF;
                        int b = (pixel >> 16) & 0xFF;
                        int a = (pixel >> 24) & 0xFF;
                        bufferedImage.setRGB(x, height - y - 1, (a << 24) | (b << 16) | (g << 8) | r);
                    }
                }
                
                image.close();
                
                // 保存截图
                saveScreenshot(bufferedImage, onComplete);
                
            } catch (Exception e) {
                LOGGER.error("截图失败: {}", e.getMessage(), e);
                isCapturing = false;
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }
    
    /**
     * 保存截图到文件
     */
    private void saveScreenshot(BufferedImage image, Runnable onComplete) {
        CompletableFuture.runAsync(() -> {
            try {
                String fileName = "latest_" + System.currentTimeMillis() + ".png";
                Path filePath = Paths.get(MGuiConstants.SCREENSHOT_CACHE_PATH, fileName);
                
                // 删除旧截图
                cleanupOldScreenshots();
                
                // 保存新截图
                ImageIO.write(image, "PNG", filePath.toFile());
                latestScreenshotPath = filePath.toString();
                
                LOGGER.info("截图保存成功: {}", latestScreenshotPath);
                
            } catch (IOException e) {
                LOGGER.error("保存截图失败: {}", e.getMessage(), e);
            } finally {
                isCapturing = false;
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }
    
    /**
     * 清理旧截图（只保留最新的）
     */
    private void cleanupOldScreenshots() {
        try {
            Path dir = Paths.get(MGuiConstants.SCREENSHOT_CACHE_PATH);
            Files.list(dir)
                .filter(p -> p.toString().endsWith(".png"))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        LOGGER.warn("删除旧截图失败: {}", path);
                    }
                });
        } catch (IOException e) {
            LOGGER.warn("清理旧截图失败: {}", e.getMessage());
        }
    }
    
    /**
     * 获取最新截图路径
     */
    public String getLatestScreenshotPath() {
        return latestScreenshotPath;
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        try {
            Path dir = Paths.get(MGuiConstants.SCREENSHOT_CACHE_PATH);
            if (Files.exists(dir)) {
                Files.list(dir).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        LOGGER.warn("删除文件失败: {}", path);
                    }
                });
            }
            LOGGER.info("截图缓存已清理");
        } catch (IOException e) {
            LOGGER.error("清理截图缓存失败: {}", e.getMessage(), e);
        }
    }
}
