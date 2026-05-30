package com.example.wgewge.client.webview;

import com.example.wgewge.MGuiConstants;
import com.example.wgewge.client.resource.MGuiResourcePackManager;
import com.example.wgewge.client.resource.UiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

/**
 * MGUI WebView 管理器
 * 使用 webviewclib/GUI.exe 启动独立的浏览器窗口
 */
public class MGuiWebViewManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MGuiWebViewManager.class);
    private static MGuiWebViewManager instance;
    
    // 默认端口
    private static final int DEFAULT_PORT = 26698;
    
    // webviewclib 路径（相对于游戏根目录）
    private static final String WEBVIEW_LIB_DIR = "webviewclib/";
    private static final String GUI_EXE_NAME = "GUI.exe";
    
    // 实际工作目录
    private File webviewLibPath;
    
    // 当前运行的进程
    private Process guiProcess;
    
    // 本地服务器实例
    private MGuiLocalHttpServer httpServer;
    
    // UI ID 到 HTML 路径的映射
    private final Map<String, String> uiHtmlPaths = new HashMap<>();
    
    // 当前打开的 UI ID
    private String currentUiId;
    
    // 进程监控线程
    private Thread processMonitorThread;
    
    // 是否正在监控
    private volatile boolean isMonitoring = false;
    
    private MGuiWebViewManager() {}
    
    public static synchronized MGuiWebViewManager getInstance() {
        if (instance == null) {
            instance = new MGuiWebViewManager();
        }
        return instance;
    }
    
    /**
     * 初始化 WebView 管理器
     */
    public void init() {
        LOGGER.info("初始化 MGUI WebView 管理器...");
        
        // 检查端口占用
        if (isPortInUse(DEFAULT_PORT)) {
            LOGGER.error("端口 {} 已被占用，可能存在另一个游戏实例正在运行！", DEFAULT_PORT);
            throw new RuntimeException("端口 " + DEFAULT_PORT + " 已被占用，无法启动游戏实例！");
        }
        
        // 解压 webviewclib 资源
        extractWebviewLib();
        
        // 启动本地 HTTP 服务器
        try {
            httpServer = new MGuiLocalHttpServer(DEFAULT_PORT);
            httpServer.start();
            LOGGER.info("本地 HTTP 服务器已启动: http://127.0.0.1:{}", DEFAULT_PORT);
        } catch (Exception e) {
            LOGGER.error("启动本地 HTTP 服务器失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法启动本地 HTTP 服务器", e);
        }
        
        // 验证 GUI.exe 是否存在
        File guiExe = getGuiExePath();
        if (!guiExe.exists()) {
            LOGGER.error("GUI.exe 不存在: {}", guiExe.getAbsolutePath());
            throw new RuntimeException("GUI.exe 文件缺失，请检查 webviewclib 资源是否正确解压");
        } else {
            LOGGER.info("GUI.exe 已找到: {}", guiExe.getAbsolutePath());
        }
        
        LOGGER.info("MGUI WebView 管理器初始化完成");
    }
    
    /**
     * 检查端口是否被占用
     */
    private boolean isPortInUse(int port) {
        try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
            socket.setReuseAddress(true);
            return false;
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 从 JAR 中解压 webviewclib 资源
     */
    private void extractWebviewLib() {
        try {
            webviewLibPath = new File(WEBVIEW_LIB_DIR);
            
            // 创建目录
            if (!webviewLibPath.exists()) {
                webviewLibPath.mkdirs();
                LOGGER.info("创建 webviewclib 目录: {}", webviewLibPath.getAbsolutePath());
            }
            
            // 从 JAR 资源中复制文件
            String[] files = {"GUI.exe", "edgeview.dll", "krnln.fnr", "mp3.run", "spec.fne"};
            
            for (String fileName : files) {
                File targetFile = new File(webviewLibPath, fileName);
                
                // 如果文件已存在，跳过（避免重复解压）
                if (targetFile.exists()) {
                    LOGGER.debug("文件已存在，跳过: {}", fileName);
                    continue;
                }
                
                // 从 JAR 资源中读取并写入文件
                String resourcePath = "/webviewclib/" + fileName;
                java.io.InputStream inputStream = getClass().getResourceAsStream(resourcePath);
                
                if (inputStream != null) {
                    try (java.io.FileOutputStream outputStream = new java.io.FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        LOGGER.info("解压文件: {} -> {}", fileName, targetFile.getAbsolutePath());
                    } finally {
                        inputStream.close();
                    }
                } else {
                    LOGGER.warn("JAR 中未找到资源: {}", resourcePath);
                }
            }
            
            LOGGER.info("webviewclib 资源解压完成");
            
        } catch (Exception e) {
            LOGGER.error("解压 webviewclib 资源失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法解压 webviewclib 资源", e);
        }
    }
    
    /**
     * 获取 GUI.exe 的完整路径
     */
    private File getGuiExePath() {
        return new File(webviewLibPath, GUI_EXE_NAME);
    }
    
    /**
     * 直接打开 URL（版本2）
     * 
     * @param url 网址
     * @param resolution 分辨率等级：1=全屏幕 2=自定义大小 3=正常预设 49=保持安静
     * @param x 窗口X坐标
     * @param y 窗口Y坐标
     */
    public void openUiDirect(String url, String resolution, String x, String y) {
        if (httpServer == null) {
            LOGGER.error("HTTP 服务器未启动");
            return;
        }
        
        // 生成唯一的 UI ID
        String uiId = "direct_" + System.currentTimeMillis();
        
        // 记录 UI 路径
        uiHtmlPaths.put(uiId, url);
        
        LOGGER.info("准备直接打开 URL: {} -> resolution={}", url, resolution);
        
        // 启动 GUI.exe 并传递参数（默认宽高为0，由GUI.exe处理）
        launchGuiExeDirect(uiId, url, resolution, "0", "0");
    }
    
    /**
     * 直接打开UI（完整参数版本）
     */
    public void openUiDirect(String url, String resolution, String width, String height, String x, String y) {
        if (httpServer == null) {
            LOGGER.error("HTTP 服务器未启动");
            return;
        }
        
        // 生成唯一的 UI ID
        String uiId = "direct_" + System.currentTimeMillis();
        
        // 记录 UI 路径
        uiHtmlPaths.put(uiId, url);
        
        LOGGER.info("准备直接打开 URL: {} -> resolution={}, width={}, height={}", 
            url, resolution, width, height);
        
        // 启动 GUI.exe 并传递参数（x, y 参数已移除，只保留4个参数）
        launchGuiExeDirect(uiId, url, resolution, width, height);
    }
    
    /**
     * 启动 GUI.exe（直接模式，带参数）
     * 参数格式: gui.exe <url> <resolution> <width> <height>
     */
    private void launchGuiExeDirect(String uiId, String url, String resolution, String width, String height) {
        try {
            File guiExe = getGuiExePath();
            
            LOGGER.info("准备启动 GUI.exe: {}", guiExe.getAbsolutePath());
            LOGGER.info("URL: {}, resolution: {}, width: {}, height: {}", url, resolution, width, height);
            
            // 构建命令: gui.exe <url> <resolution> <width> <height>
            ProcessBuilder processBuilder = new ProcessBuilder(
                guiExe.getAbsolutePath(),
                url,
                resolution,
                width,
                height
            );
            
            // 设置工作目录为 webviewclib
            processBuilder.directory(webviewLibPath);
            
            // 合并错误输出
            processBuilder.redirectErrorStream(true);
            
            // 启动进程
            guiProcess = processBuilder.start();
            
            // 记录当前 UI ID
            currentUiId = uiId;
            
            // 启动进程监控
            startProcessMonitor(uiId);
            
            // 读取输出（在后台线程）
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(guiProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOGGER.debug("GUI.exe 输出: {}", line);
                    }
                } catch (Exception e) {
                    LOGGER.error("读取 GUI.exe 输出失败: {}", e.getMessage());
                }
            }, "GUI-Output-Reader").start();
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (guiProcess != null && guiProcess.isAlive()) {
                    LOGGER.info("关闭 GUI.exe 进程...");
                    guiProcess.destroy();
                }
            }));
            
            LOGGER.info("GUI.exe 已启动（直接模式）");
            
        } catch (Exception e) {
            LOGGER.error("启动 GUI.exe 失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 打开 UI 窗口
     * 
     * @param uiId UI 标识
     * @param htmlPath HTML 文件路径
     */
    public void openUi(String uiId, String htmlPath) {
        if (httpServer == null) {
            LOGGER.error("HTTP 服务器未启动");
            return;
        }
        
        // 注册 UI 路径
        uiHtmlPaths.put(uiId, htmlPath);
        
        // 转换为 file:// URL
        File htmlFile = new File(htmlPath);
        if (!htmlFile.exists()) {
            LOGGER.error("HTML 文件不存在: {}", htmlPath);
            return;
        }
        
        String fileUrl = "file://" + htmlFile.getAbsolutePath().replace("\\", "/");
        LOGGER.info("准备打开 UI: {} -> {}", uiId, fileUrl);
        
        // 启动 GUI.exe
        launchGuiExe(uiId, fileUrl);
    }
    
    /**
     * 启动 GUI.exe（使用 set.json 配置）
     * 参数格式: gui.exe <url> <resolution> <width> <height>
     */
    private void launchGuiExe(String uiId, String url) {
        try {
            File guiExe = getGuiExePath();
            
            // 从资源包读取 set.json 配置
            String basePath = url;
            if (basePath.startsWith("file://")) {
                basePath = basePath.substring(7);
            }
            // 移除入口文件名，获取基础路径
            int lastSlash = basePath.lastIndexOf('/');
            if (lastSlash > 0) {
                basePath = basePath.substring(0, lastSlash);
            }
            
            // 加载 set.json 配置
            UiConfig config = UiConfig.loadFromSetJson(basePath);
            LOGGER.info("读取 set.json 配置: {}", config);
            
            // 构建命令参数
            String entryUrl = url;
            if (config.getEntry() != null && !config.getEntry().isEmpty()) {
                // 使用配置中的入口文件
                entryUrl = "file:///" + basePath.replace("\\", "/") + "/" + config.getEntry();
            }
            
            // 构建命令: gui.exe "url" "resolution" "width" "height"
            LOGGER.info("准备启动 GUI.exe: {}", guiExe.getAbsolutePath());
            LOGGER.info("URL: {}", entryUrl);
            LOGGER.info("参数: resolution={}, width={}, height={}", 
                config.getResolution(), config.getWidth(), config.getHeight());
            
            // 启动进程
            ProcessBuilder processBuilder = new ProcessBuilder(
                guiExe.getAbsolutePath(),
                entryUrl,
                String.valueOf(config.getResolution()),
                String.valueOf(config.getWidth()),
                String.valueOf(config.getHeight())
            );
            
            // 设置工作目录为 webviewclib
            processBuilder.directory(webviewLibPath);
            
            // 合并错误输出
            processBuilder.redirectErrorStream(true);
            
            // 启动进程
            guiProcess = processBuilder.start();
            
            // 记录当前 UI ID
            currentUiId = uiId;
            
            // 启动进程监控
            startProcessMonitor(uiId);
            
            // 读取输出（在后台线程）
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(guiProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOGGER.debug("GUI.exe 输出: {}", line);
                    }
                } catch (Exception e) {
                    LOGGER.error("读取 GUI.exe 输出失败: {}", e.getMessage());
                }
            }, "GUI-Output-Reader").start();
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (guiProcess != null && guiProcess.isAlive()) {
                    LOGGER.info("关闭 GUI.exe 进程...");
                    guiProcess.destroy();
                }
            }));
            
            LOGGER.info("GUI.exe 已启动");
            
        } catch (Exception e) {
            LOGGER.error("启动 GUI.exe 失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 启动进程监控线程
     */
    private void startProcessMonitor(String uiId) {
        // 停止之前的监控
        stopProcessMonitor();
        
        isMonitoring = true;
        processMonitorThread = new Thread(() -> {
            LOGGER.info("开始监控 GUI.exe 进程...");
            
            while (isMonitoring) {
                try {
                    // 检查进程是否存活
                    if (guiProcess != null) {
                        try {
                            // exitValue() 会抛出异常如果进程还在运行
                            int exitCode = guiProcess.exitValue();
                            LOGGER.warn("GUI.exe 进程已退出，退出码: {}", exitCode);
                            
                            // 进程已关闭，通知游戏关闭 UI
                            onGuiProcessClosed(uiId);
                            break;
                        } catch (IllegalThreadStateException e) {
                            // 进程还在运行，正常情况
                            Thread.sleep(500); // 每 500ms 检查一次
                        }
                    } else {
                        // 进程对象为 null，说明已被手动关闭
                        break;
                    }
                } catch (InterruptedException e) {
                    LOGGER.debug("进程监控线程被中断");
                    break;
                } catch (Exception e) {
                    LOGGER.error("进程监控异常: {}", e.getMessage(), e);
                    break;
                }
            }
            
            LOGGER.info("GUI.exe 进程监控结束");
        }, "GUI-Process-Monitor");
        
        processMonitorThread.setDaemon(true);
        processMonitorThread.start();
    }
    
    /**
     * 停止进程监控
     */
    private void stopProcessMonitor() {
        isMonitoring = false;
        if (processMonitorThread != null && processMonitorThread.isAlive()) {
            processMonitorThread.interrupt();
            try {
                processMonitorThread.join(1000);
            } catch (InterruptedException e) {
                LOGGER.warn("等待监控线程结束超时");
            }
        }
    }
    
    /**
     * 当 GUI 进程关闭时的回调
     */
    private void onGuiProcessClosed(String uiId) {
        LOGGER.info("检测到 GUI.exe 窗口已关闭，准备返回游戏...");
        
        // 清理进程
        guiProcess = null;
        currentUiId = null;
        
        // 在游戏主线程中关闭 UI 屏幕
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> {
                if (minecraft.screen != null) {
                    LOGGER.info("关闭当前屏幕，返回游戏");
                    minecraft.setScreen(null);
                }
            });
        }
        
        // 停止监控
        stopProcessMonitor();
    }
    
    /**
     * 关闭 UI 窗口
     */
    public void closeUi(String uiId) {
        LOGGER.info("关闭 UI: {}", uiId);
        
        // 停止进程监控
        stopProcessMonitor();
        
        // 移除 UI 路径
        uiHtmlPaths.remove(uiId);
        currentUiId = null;
        
        // 如果只有一个 UI 或没有 UI，可以关闭进程
        if (uiHtmlPaths.isEmpty() && guiProcess != null && guiProcess.isAlive()) {
            LOGGER.info("所有 UI 已关闭，终止 GUI.exe 进程");
            guiProcess.destroy();
            guiProcess = null;
        }
    }
    
    /**
     * 获取本地服务器端口
     */
    public int getServerPort() {
        return DEFAULT_PORT;
    }
    
    /**
     * 获取 HTTP 服务器实例
     */
    public MGuiLocalHttpServer getHttpServer() {
        return httpServer;
    }
    
    /**
     * 检查指定 UI 是否正在打开
     */
    public boolean isUiOpen(String uiId) {
        return uiId != null && uiId.equals(currentUiId) && 
               guiProcess != null && guiProcess.isAlive();
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        LOGGER.info("清理 MGUI WebView 管理器...");
        
        // 停止进程监控
        stopProcessMonitor();
        
        // 关闭 GUI 进程
        if (guiProcess != null && guiProcess.isAlive()) {
            guiProcess.destroy();
            guiProcess = null;
        }
        
        // 停止 HTTP 服务器
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        
        // 清空 UI 路径
        uiHtmlPaths.clear();
        
        LOGGER.info("MGUI WebView 管理器已清理");
    }
}