package com.example.wgewge.client.resource;

import com.example.wgewge.MGuiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * UI资源包管理器
 * 负责下载、解压和管理HTML资源包
 * 支持MD5缓存校验机制
 */
public class MGuiResourcePackManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MGuiResourcePackManager.class);
    private static MGuiResourcePackManager instance;
    
    // UI资源路径映射 <uiId, basePath>
    private final Map<String, String> uiResourcePaths = new HashMap<>();
    
    // UI资源MD5缓存 <uiId, md5Hash>
    private final Map<String, String> uiMd5Cache = new HashMap<>();
    
    // MD5缓存文件路径
    private static final String MD5_CACHE_FILE = "mgui/cache/md5_cache.json";
    
    private MGuiResourcePackManager() {}
    
    public static synchronized MGuiResourcePackManager getInstance() {
        if (instance == null) {
            instance = new MGuiResourcePackManager();
        }
        return instance;
    }
    
    /**
     * 初始化
     */
    public void init() {
        try {
            // 创建资源包根目录
            Path resourceDir = Paths.get(MGuiConstants.RESOURCE_PACK_BASE_PATH);
            Files.createDirectories(resourceDir);
            
            // 创建缓存目录
            Path cacheDir = Paths.get("mgui/cache");
            Files.createDirectories(cacheDir);
            
            // 加载MD5缓存
            loadMd5Cache();
            
            LOGGER.info("资源包管理器初始化完成: {}", resourceDir.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("创建资源目录失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 检查缓存是否有效
     * 
     * @param alias UI别名
     * @param newMd5 新的MD5值
     * @return 如果缓存有效返回true，否则返回false
     */
    public boolean isCacheValid(String alias, String newMd5) {
        String cachedMd5 = uiMd5Cache.get(alias);
        if (cachedMd5 != null && cachedMd5.equals(newMd5)) {
            // 检查资源文件是否存在
            String htmlPath = getHtmlPath(alias);
            if (new java.io.File(htmlPath).exists()) {
                LOGGER.info("缓存有效，跳过下载: {}", alias);
                return true;
            }
        }
        return false;
    }
    
    /**
     * 计算字节数组的MD5值
     * 
     * @param data 数据
     * @return MD5字符串
     */
    public String calculateMd5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("计算MD5失败", e);
            return "";
        }
    }
    
    /**
     * 更新MD5缓存
     * 
     * @param alias UI别名
     * @param md5 MD5值
     */
    public void updateMd5Cache(String alias, String md5) {
        uiMd5Cache.put(alias, md5);
        saveMd5Cache();
    }
    
    /**
     * 加载MD5缓存文件
     */
    private void loadMd5Cache() {
        try {
            Path cachePath = Paths.get(MD5_CACHE_FILE);
            if (Files.exists(cachePath)) {
                String json = new String(Files.readAllBytes(cachePath));
                com.google.gson.JsonObject jsonObj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                for (String key : jsonObj.keySet()) {
                    uiMd5Cache.put(key, jsonObj.get(key).getAsString());
                }
                LOGGER.info("已加载 {} 个MD5缓存记录", uiMd5Cache.size());
            }
        } catch (Exception e) {
            LOGGER.warn("加载MD5缓存失败: {}", e.getMessage());
        }
    }
    
    /**
     * 保存MD5缓存文件
     */
    private void saveMd5Cache() {
        try {
            com.google.gson.JsonObject jsonObj = new com.google.gson.JsonObject();
            for (Map.Entry<String, String> entry : uiMd5Cache.entrySet()) {
                jsonObj.addProperty(entry.getKey(), entry.getValue());
            }
            String json = new com.google.gson.Gson().toJson(jsonObj);
            Files.write(Paths.get(MD5_CACHE_FILE), json.getBytes());
            LOGGER.debug("MD5缓存已保存");
        } catch (Exception e) {
            LOGGER.error("保存MD5缓存失败: {}", e.getMessage());
        }
    }
    
    /**
     * 解压ZIP数据到缓存目录
     * 
     * @param alias UI别名
     * @param zipData ZIP文件的字节数据
     * @return 解压后的目录路径
     */
    public String extractZipToCache(String alias, byte[] zipData) {
        try {
            String uiDir = MGuiConstants.RESOURCE_PACK_BASE_PATH + alias;
            Path uiPath = Paths.get(uiDir);
            
            // 创建目录
            Files.createDirectories(uiPath);
            
            // 将ZIP数据写入临时文件
            Path tempZip = uiPath.resolve("temp.zip");
            Files.write(tempZip, zipData);
            
            // 解压ZIP
            unzipFile(tempZip.toFile(), uiPath.toFile());
            
            // 删除临时ZIP文件
            Files.deleteIfExists(tempZip);
            
            // 更新MD5缓存
            String md5 = calculateMd5(zipData);
            updateMd5Cache(alias, md5);
            
            LOGGER.info("ZIP解压完成: {}, MD5: {}", uiDir, md5);
            return uiDir;
            
        } catch (Exception e) {
            LOGGER.error("解压ZIP失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 解压ZIP文件
     */
    private void unzipFile(java.io.File zipFile, java.io.File destDir) throws IOException {
        try (java.util.zip.ZipInputStream zipIn = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(zipFile))) {
            
            java.util.zip.ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                Path filePath = Paths.get(destDir.getAbsolutePath(), entry.getName());
                
                if (!entry.isDirectory()) {
                    // 确保父目录存在
                    Files.createDirectories(filePath.getParent());
                    
                    // 解压文件
                    try (java.io.OutputStream outputStream = Files.newOutputStream(filePath)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = zipIn.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                }
                
                zipIn.closeEntry();
            }
        }
    }
    
    /**
     * 获取UI的基础路径
     */
    public String getUiBasePath(String alias) {
        String basePath = uiResourcePaths.get(alias);
        if (basePath == null) {
            basePath = MGuiConstants.RESOURCE_PACK_BASE_PATH + alias;
        }
        return basePath;
    }
    
    /**
     * 获取HTML文件路径（统一使用 lag.html 作为入口）
     */
    public String getHtmlPath(String alias) {
        return getUiBasePath(alias) + "/lag.html";
    }
    
    /**
     * 获取UI配置（从 set.json 读取）
     */
    public UiConfig getUiConfig(String alias) {
        String basePath = getUiBasePath(alias);
        return UiConfig.loadFromSetJson(basePath);
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        uiResourcePaths.clear();
        LOGGER.info("资源包管理器已清理");
    }
    
    /**
     * 清除所有已下载的资源包缓存
     */
    public void clearAllCache() throws IOException {
        // 删除所有UI资源目录
        Path resourceDir = Paths.get(MGuiConstants.RESOURCE_PACK_BASE_PATH);
        if (Files.exists(resourceDir)) {
            deleteDirectory(resourceDir);
            LOGGER.info("已清除所有资源包目录");
        }
        
        // 清空MD5缓存
        uiMd5Cache.clear();
        
        // 删除MD5缓存文件
        Path cacheFile = Paths.get(MD5_CACHE_FILE);
        if (Files.exists(cacheFile)) {
            Files.delete(cacheFile);
            LOGGER.info("已清除MD5缓存文件");
        }
        
        // 重新创建目录结构
        Files.createDirectories(resourceDir);
        Path cacheDir = Paths.get("mgui/cache");
        Files.createDirectories(cacheDir);
        
        LOGGER.info("所有资源包缓存已清除");
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                entries.forEach(entry -> {
                    try {
                        deleteDirectory(entry);
                    } catch (IOException e) {
                        LOGGER.error("删除文件失败: {}", entry);
                    }
                });
            }
        }
        Files.delete(path);
    }
}