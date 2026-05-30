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
     * 当前 webviewclib 资源版本（用于检测是否需要更新）
     */
    private static final String WEBVIEW_LIB_VERSION = "2.0.0";
    
    /**
     * webviewclib 所需的所有文件列表（与项目根目录中的 webviewclib 文件夹保持同步）
     */
    private static final String[] WEBVIEW_LIB_FILES = {
        "GUI.exe",
        "edgeview.dll",
        "iext2.fne",
        "krnln.fnr",
        "mp3.run",
        "msgsrv.exe",
        "spec.fne"
    };
    
    /**
     * 从 JAR 中解压 webviewclib 资源（带版本检查）
     */
    private void extractWebviewLib() {
        try {
            webviewLibPath = new File(WEBVIEW_LIB_DIR);
            
            // 创建目录
            if (!webviewLibPath.exists()) {
                webviewLibPath.mkdirs();
                LOGGER.info("创建 webviewclib 目录: {}", webviewLibPath.getAbsolutePath());
            }
            
            // 检查版本文件，决定是否需要重新解压
            File versionFile = new File(webviewLibPath, "version.txt");
            boolean needUpdate = !versionFile.exists();
            
            if (!needUpdate) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(versionFile))) {
                    String existingVersion = reader.readLine();
                    needUpdate = !WEBVIEW_LIB_VERSION.equals(existingVersion);
                }
            }
            
            LOGGER.info("准备解压 {} 个 webviewclib 文件", WEBVIEW_LIB_FILES.length);
            
            for (String fileName : WEBVIEW_LIB_FILES) {
                File targetFile = new File(webviewLibPath, fileName);
                
                // 如果文件已存在且不需要更新，跳过
                if (targetFile.exists() && !needUpdate) {
                    LOGGER.debug("文件已存在且版本一致，跳过: {}", fileName);
                    continue;
                }
                
                // 如果需要更新且文件已存在，先删除旧文件
                if (targetFile.exists() && needUpdate) {
                    targetFile.delete();
                    LOGGER.info("删除旧版本文件: {}", fileName);
                }
                
                // 从 JAR 资源中读取并写入文件
                String resourcePath = "/webviewclib/" + fileName;
                java.io.InputStream inputStream = getClass().getResourceAsStream(resourcePath);
                
                if (inputStream != null) {
                    try (java.io.FileOutputStream outputStream = new java.io.FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long totalBytes = 0;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            totalBytes += bytesRead;
                        }
                        LOGGER.info("{}文件: {} ({} bytes) -> {}", needUpdate ? "更新" : "解压", fileName, totalBytes, targetFile.getAbsolutePath());
                    } finally {
                        inputStream.close();
                    }
                } else {
                    LOGGER.warn("JAR 中未找到资源: {}", resourcePath);
                }
            }
            
            // 更新版本文件
            if (needUpdate) {
                try (java.io.PrintWriter writer = new java.io.PrintWriter(versionFile)) {
                    writer.println(WEBVIEW_LIB_VERSION);
                }
                LOGGER.info("更新 webviewclib 版本: {}", WEBVIEW_LIB_VERSION);
            }
            
            LOGGER.info("webviewclib 资源解压完成，共 {} 个文件", WEBVIEW_LIB_FILES.length);
            
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
     * 直接打开UI
     * 参数格式: gui.exe <resolution> <width> <height> <url>
     * 分辨率等级：1=全屏幕 2=自定义大小 3=正常预设 49=保持安静
     */
    public void openUiDirect(String url, String resolution, String width, String height) {
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
        
        // 启动 GUI.exe 并传递参数（4个参数：url, resolution, width, height）
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
            
            // 构建命令: gui.exe "<url>" "<resolution>" "<width>" "<height>"
            // 参数添加双引号防止特殊字符吞参数
            ProcessBuilder processBuilder = new ProcessBuilder(
                guiExe.getAbsolutePath(),
                "\"" + url + "\"",
                "\"" + resolution + "\"",
                "\"" + width + "\"",
                "\"" + height + "\""
            );
            
            // 设置工作目录为 webviewclib
            processBuilder.directory(webviewLibPath);
            
            // 合并错误输出
            processBuilder.redirectErrorStream(true);
            
            // 输出完整命令行
            LOGGER.info("启动命令: {}", String.join(" ", processBuilder.command()));
            
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
     * 参数格式: gui.exe <resolution> <width> <height> <url>
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
            // 参数添加双引号防止特殊字符吞参数
            ProcessBuilder processBuilder = new ProcessBuilder(
                guiExe.getAbsolutePath(),
                "\"" + entryUrl + "\"",
                "\"" + String.valueOf(config.getResolution()) + "\"",
                "\"" + String.valueOf(config.getWidth()) + "\"",
                "\"" + String.valueOf(config.getHeight()) + "\""
            );
            
            // 设置工作目录为 webviewclib
            processBuilder.directory(webviewLibPath);
            
            // 合并错误输出
            processBuilder.redirectErrorStream(true);
            
            // 输出完整命令行
            LOGGER.info("启动命令: {}", String.join(" ", processBuilder.command()));
            
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
     * @param uiId UI ID 或 "all" 表示关闭所有
     */
    public void closeUi(String uiId) {
        LOGGER.info("关闭 UI: {}", uiId);
        
        // 停止进程监控
        stopProcessMonitor();
        
        // 如果是 "all"，清空所有 UI
        if ("all".equals(uiId)) {
            uiHtmlPaths.clear();
            currentUiId = null;
            
            // 强制终止 GUI.exe 进程（使用强制方式）
            terminateGuiProcess();
        } else {
            // 移除指定 UI 路径
            uiHtmlPaths.remove(uiId);
            currentUiId = null;
            
            // 如果没有 UI 了，关闭进程
            if (uiHtmlPaths.isEmpty()) {
                terminateGuiProcess();
            }
        }
    }
    
    /**
     * 强制终止 GUI.exe 进程（通过路径查找并终止）
     */
    private void terminateGuiProcess() {
        // 首先尝试使用保存的进程对象终止
        if (guiProcess != null && guiProcess.isAlive()) {
            try {
                LOGGER.info("尝试终止 GUI.exe 进程 (PID: {})", guiProcess.pid());
                guiProcess.destroy();
                
                if (guiProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    LOGGER.info("GUI.exe 进程已优雅退出");
                } else {
                    LOGGER.warn("GUI.exe 进程未响应，强制杀死");
                    guiProcess.destroyForcibly();
                    guiProcess.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
                    LOGGER.info("GUI.exe 进程已强制终止");
                }
                
                guiProcess = null;
            } catch (Exception e) {
                LOGGER.warn("通过进程对象终止失败，尝试通过路径查找: {}", e.getMessage());
            }
        }
        
        // 通过路径查找并终止 GUI.exe 进程（备用方案）
        terminateGuiProcessByPath();
    }
    
    /**
     * 通过路径查找并终止 GUI.exe 进程
     */
    private void terminateGuiProcessByPath() {
        if (webviewLibPath == null) {
            LOGGER.warn("webviewLibPath 为空，无法通过路径终止进程");
            return;
        }
        
        File guiExeFile = new File(webviewLibPath, "GUI.exe");
        String guiExePath = guiExeFile.getAbsolutePath();
        
        try {
            // 使用 taskkill 命令强制终止所有 GUI.exe 进程
            ProcessBuilder pb = new ProcessBuilder(
                "taskkill", "/F", "/IM", "GUI.exe"
            );
            pb.redirectErrorStream(true);
            Process killProcess = pb.start();
            
            boolean completed = killProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (completed) {
                int exitCode = killProcess.exitValue();
                if (exitCode == 0) {
                    LOGGER.info("通过 taskkill 成功终止 GUI.exe 进程");
                } else {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(killProcess.getInputStream()))) {
                        String line;
                        StringBuilder output = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                        if (!output.toString().contains("找不到进程")) {
                            LOGGER.warn("taskkill 退出码: {}, 输出: {}", exitCode, output.toString());
                        }
                    }
                }
            } else {
                LOGGER.warn("taskkill 命令执行超时");
            }
            
            guiProcess = null;
        } catch (Exception e) {
            LOGGER.warn("通过路径终止 GUI.exe 进程失败: {}", e.getMessage());
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