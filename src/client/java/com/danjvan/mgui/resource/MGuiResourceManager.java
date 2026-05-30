package com.danjvan.mgui.resource;

import com.danjvan.mgui.util.MGuiLogger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MGuiResourceManager {
    private static MGuiResourceManager instance;
    
    private final Path mguiDir;
    private final Map<String, PluginInfo> loadedPlugins;
    
    public static class PluginInfo {
        public String pluginId;
        public String zipPath;
        public String hash;
        public Path extractPath;
        public JsonObject resJson;
        public JsonObject pakJson;
        
        public PluginInfo(String pluginId) {
            this.pluginId = pluginId;
        }
    }
    
    private MGuiResourceManager() {
        this.loadedPlugins = new HashMap<>();
        
        // 初始化 MGUI 目录
        String serverAddress = getCurrentServerAddress();
        this.mguiDir = Paths.get("mc/1.21.8/mgui/" + serverAddress);
        
        try {
            Files.createDirectories(this.mguiDir);
            Files.createDirectories(this.mguiDir.resolve("pak"));
            Files.createDirectories(this.mguiDir.resolve("gui"));
            MGuiLogger.info("MGUI 目录初始化: " + this.mguiDir.toAbsolutePath());
        } catch (IOException e) {
            MGuiLogger.error("创建 MGUI 目录失败: " + e.getMessage());
        }
    }
    
    public static MGuiResourceManager getInstance() {
        if (instance == null) {
            instance = new MGuiResourceManager();
        }
        return instance;
    }
    
    /**
     * 请求资源包（SFV 注册流程）
     */
    public void requestResourcePack(String pluginId) {
        MGuiLogger.info("开始请求资源包: " + pluginId);
        
        // 检查 mgui 文件夹
        Path pluginDir = mguiDir.resolve("gui").resolve(pluginId);
        if (!Files.exists(pluginDir)) {
            try {
                Files.createDirectories(pluginDir);
                MGuiLogger.info("创建插件目录: " + pluginDir);
            } catch (IOException e) {
                MGuiLogger.error("创建插件目录失败: " + e.getMessage());
                return;
            }
        }
        
        // 异步拉取 res.json
        downloadResJson(pluginId);
    }
    
    /**
     * 下载 res.json（CCIL 流程步骤1）
     */
    private void downloadResJson(String pluginId) {
        new Thread(() -> {
            try {
                String resJsonUrl = "http://localhost:27890/res/" + pluginId + "/res.json";
                String resJsonContent = downloadFile(resJsonUrl);
                
                if (resJsonContent != null) {
                    Path resJsonPath = mguiDir.resolve("res.json");
                    Files.writeString(resJsonPath, resJsonContent);
                    
                    JsonObject resJson = JsonParser.parseString(resJsonContent).getAsJsonObject();
                    
                    // 解析并下载所有包
                    parseAndDownloadPacks(resJson);
                }
            } catch (Exception e) {
                MGuiLogger.error("下载 res.json 失败: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 解析并下载所有包
     */
    private void parseAndDownloadPacks(JsonObject resJson) {
        int playerCount = getPlayerCount();
        
        for (Map.Entry<String, com.google.gson.JsonElement> entry : resJson.entrySet()) {
            String packName = entry.getKey();
            
            if (playerCount < 15) {
                // 人数 < 15：下载所有包
                downloadPack(packName);
            } else {
                // 人数 >= 15：按需处理（这里简化为也下载）
                downloadPack(packName);
            }
        }
    }
    
    /**
     * 下载单个包并校验 hash
     */
    private void downloadPack(String packName) {
        new Thread(() -> {
            try {
                // 下载 pak.json
                String pakJsonUrl = "http://localhost:27890/res/" + packName + "/pak.json";
                String pakJsonContent = downloadFile(pakJsonUrl);
                
                if (pakJsonContent == null) return;
                
                JsonObject pakJson = JsonParser.parseString(pakJsonContent).getAsJsonObject();
                
                // 校验每个文件的 hash
                for (Map.Entry<String, com.google.gson.JsonElement> entry : pakJson.entrySet()) {
                    String fileName = entry.getKey();
                    String expectedHash = entry.getValue().getAsString();
                    
                    Path localFile = mguiDir.resolve("gui").resolve(packName).resolve(fileName);
                    
                    if (Files.exists(localFile)) {
                        // 校验本地 hash
                        String localHash = calculateFileHash(localFile);
                        if (localHash.equals(expectedHash)) {
                            MGuiLogger.debug("文件 hash 匹配，跳过: " + fileName);
                            continue;
                        }
                    }
                    
                    // 下载文件
                    String fileUrl = "http://localhost:27890/res/" + packName + "/" + fileName;
                    downloadFileToFile(fileUrl, localFile);
                    
                    MGuiLogger.info("下载完成: " + fileName);
                }
                
            } catch (Exception e) {
                MGuiLogger.error("下载包失败 [" + packName + "]: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 从 URL 下载文件内容
     */
    private String downloadFile(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                reader.close();
                return content.toString();
            }
        } catch (Exception e) {
            MGuiLogger.error("下载文件失败: " + url + " - " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 下载文件到指定路径
     */
    private void downloadFileToFile(String url, Path targetPath) {
        try {
            Files.createDirectories(targetPath.getParent());
            
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream()) {
                    Files.copy(in, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            MGuiLogger.error("下载文件失败: " + url + " - " + e.getMessage());
        }
    }
    
    /**
     * 计算文件 hash
     */
    private String calculateFileHash(Path filePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = md.digest(fileBytes);
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            MGuiLogger.error("计算 hash 失败: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * 解压 zip 文件
     */
    public void extractZip(String zipPath, Path targetDir) {
        try {
            Files.createDirectories(targetDir);
            
            try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipPath))) {
                ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    Path filePath = targetDir.resolve(entry.getName());
                    
                    if (!entry.isDirectory()) {
                        Files.createDirectories(filePath.getParent());
                        Files.copy(zipIn, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    zipIn.closeEntry();
                }
            }
            
            MGuiLogger.info("解压完成: " + zipPath);
        } catch (IOException e) {
            MGuiLogger.error("解压失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取当前服务器地址
     */
    private String getCurrentServerAddress() {
        Minecraft client = Minecraft.getInstance();
        if (client.getCurrentServer() != null) {
            return client.getCurrentServer().ip.replace(":", "_");
        }
        return "localhost";
    }
    
    /**
     * 获取当前玩家数量
     */
    private int getPlayerCount() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            return client.level.players().size();
        }
        return 0;
    }
}
