package com.example.wgewge.client.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * UI配置类
 * 从资源包中的 set.json 读取配置参数
 * 
 * set.json 格式示例:
 * {
 *   "resolution": 2,
 *   "width": 1280,
 *   "height": 720,
 *   "x": 0,
 *   "y": 0,
 *   "entry": "index.html"
 * }
 * 
 * 分辨率等级:
 * 1 = 全屏
 * 2 = 自定义大小
 * 3 = 正常预设
 * 49 = 保持安静
 */
public class UiConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(UiConfig.class);
    
    // 分辨率等级
    private int resolution;
    
    // 窗口宽度（分辨率等级为2时使用）
    private int width;
    
    // 窗口高度（分辨率等级为2时使用）
    private int height;
    
    // 窗口位置X（分辨率等级为2时使用）
    private int x;
    
    // 窗口位置Y（分辨率等级为2时使用）
    private int y;
    
    // 入口HTML文件
    private String entry;
    
    // 是否有效
    private boolean valid;
    
    // 默认配置
    public UiConfig() {
        this.resolution = 3; // 默认正常预设
        this.width = 1280;
        this.height = 720;
        this.x = 0;
        this.y = 0;
        this.entry = "lag.html";
        this.valid = true;
    }
    
    /**
     * 从 set.json 文件加载配置
     * 
     * @param resourcePath 资源包路径
     * @return UiConfig 配置对象
     */
    public static UiConfig loadFromSetJson(String resourcePath) {
        UiConfig config = new UiConfig();
        
        try {
            Path setJsonPath = Paths.get(resourcePath, "set.json");
            if (Files.exists(setJsonPath)) {
                String jsonContent = new String(Files.readAllBytes(setJsonPath));
                JsonObject jsonObj = JsonParser.parseString(jsonContent).getAsJsonObject();
                
                // 读取分辨率等级
                if (jsonObj.has("resolution")) {
                    config.resolution = jsonObj.get("resolution").getAsInt();
                }
                
                // 读取窗口参数
                if (jsonObj.has("width")) {
                    config.width = jsonObj.get("width").getAsInt();
                }
                if (jsonObj.has("height")) {
                    config.height = jsonObj.get("height").getAsInt();
                }
                if (jsonObj.has("x")) {
                    config.x = jsonObj.get("x").getAsInt();
                }
                if (jsonObj.has("y")) {
                    config.y = jsonObj.get("y").getAsInt();
                }
                
                // 读取入口文件
                if (jsonObj.has("entry")) {
                    config.entry = jsonObj.get("entry").getAsString();
                }
                
                config.valid = true;
                LOGGER.info("成功加载 set.json 配置: resolution={}, width={}, height={}, entry={}", 
                    config.resolution, config.width, config.height, config.entry);
            } else {
                LOGGER.debug("未找到 set.json，使用默认配置");
                config.valid = true;
            }
        } catch (Exception e) {
            LOGGER.warn("加载 set.json 失败，使用默认配置: {}", e.getMessage());
            config.valid = true; // 即使加载失败，也使用默认配置
        }
        
        return config;
    }
    
    // Getter methods
    public int getResolution() {
        return resolution;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public int getX() {
        return x;
    }
    
    public int getY() {
        return y;
    }
    
    public String getEntry() {
        return entry;
    }
    
    public boolean isValid() {
        return valid;
    }
    
    /**
     * 获取GUI.exe命令行参数数组
     * 格式: ["url", "resolution", "width", "height", "x", "y"]
     */
    public String[] getGuiExeArgs(String baseUrl) {
        String entryUrl = baseUrl;
        if (!baseUrl.contains("file://")) {
            entryUrl = "file:///" + baseUrl.replace("\\", "/");
        }
        if (!entryUrl.endsWith("/")) {
            entryUrl += "/";
        }
        entryUrl += entry;
        
        return new String[] {
            entryUrl,
            String.valueOf(resolution),
            String.valueOf(width),
            String.valueOf(height),
            String.valueOf(x),
            String.valueOf(y)
        };
    }
    
    @Override
    public String toString() {
        return "UiConfig{" +
            "resolution=" + resolution +
            ", width=" + width +
            ", height=" + height +
            ", x=" + x +
            ", y=" + y +
            ", entry='" + entry + '\'' +
            ", valid=" + valid +
            '}';
    }
}