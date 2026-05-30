package com.example.wgewge.client;

import com.example.wgewge.client.gui.MGuiHtmlScreen;
import com.example.wgewge.client.resource.MGuiResourcePackManager;
import com.example.wgewge.client.screenshot.ScreenshotManager;
import com.example.wgewge.client.webview.MGuiWebViewManager;
import com.example.wgewge.network.MGuiPacket;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MGUI 客户端模组
 * 负责：
 * 1. 接收服务器发送的UI打开指令
 * 2. 下载和管理HTML资源包（支持MD5缓存校验）
 * 3. 渲染HTML界面（带进度条）
 * 4. 提供JS接口（截图、执行命令等）
 * 5. Toast通知系统
 */
public class MGuiClientMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("mgui_client");
    private static MGuiClientMod instance;
    
    // 下载进度状态
    private volatile int downloadProgress = 0;
    private volatile int downloadTotal = 0;
    private volatile boolean isDownloading = false;
    
    // 清理资源包快捷键
    private KeyMapping clearCacheKey;
    
    public static MGuiClientMod getInstance() {
        return instance;
    }
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("MGUI 客户端模组初始化...");
        
        // 保存实例引用
        instance = this;
        
        // 初始化管理器
        initManagers();
        
        // 注册网络监听器
        registerNetworkListeners();
        
        // 注册事件
        registerEvents();
        
        // 注册渲染事件（用于绘制进度条和Toast）
        registerRenderEvents();
        
        // 注册快捷键
        registerKeyBindings();
        
        LOGGER.info("MGUI 客户端模组初始化完成");
    }
    
    /**
     * 初始化管理器
     */
    private void initManagers() {
        // 初始化资源包管理器
        MGuiResourcePackManager.getInstance().init();
        
        // 初始化截图管理器
        ScreenshotManager.getInstance().init();
        
        // 初始化 WebView 管理器（会启动本地 HTTP 服务器并检测端口占用）
        try {
            MGuiWebViewManager.getInstance().init();
            LOGGER.info("WebView 管理器初始化成功");
        } catch (Exception e) {
            LOGGER.error("WebView 管理器初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法初始化 WebView 管理器，游戏将退出", e);
        }
        
        LOGGER.info("管理器初始化完成");
    }
    
    /**
     * 注册网络监听器
     */
    private void registerNetworkListeners() {
        // 监听服务器到客户端的消息
        ClientPlayNetworking.registerGlobalReceiver(
            MGuiPacket.S2CPacket.TYPE,
            (payload, context) -> {
                String jsonData = ((MGuiPacket.S2CPacket) payload).getData();
                context.client().execute(() -> handleServerMessage(jsonData));
            }
        );
        
        LOGGER.info("网络监听器注册完成");
    }
    
    /**
     * 处理服务器消息（Fabric CustomPacketPayload）
     */
    private void handleServerMessage(String jsonData) {
        processServerMessage(jsonData);
    }
    
    /**
     * 处理来自 Plugin Message 的服务器消息（Bukkit/Paper 兼容）
     */
    public void handleServerMessageFromPluginMessage(String jsonData) {
        LOGGER.debug("收到 Plugin Message: {}", jsonData.substring(0, Math.min(50, jsonData.length())));
        processServerMessage(jsonData);
    }
    
    /**
     * 统一处理服务器消息
     */
    private void processServerMessage(String jsonData) {
        try {
            JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
            String type = json.get("type").getAsString();
            
            switch (type) {
                case "open_ui":
                    handleOpenUi(json);
                    break;
                case "open_ui_direct":
                    handleOpenUiDirect(json);
                    break;
                case "zip_chunk":
                    handleZipChunk(json);
                    break;
                case "zip_complete":
                    handleZipComplete();
                    break;
                case "close_ui":
                    handleCloseUi();
                    break;
                case "ui_registered":
                    handleUiRegistered(json);
                    break;
                default:
                    LOGGER.warn("未知的消息类型: {}", type);
            }
        } catch (Exception e) {
            LOGGER.error("处理服务器消息失败: {}", e.getMessage(), e);
        }
    }
    
    // ZIP数据接收缓冲区
    private final java.util.List<byte[]> zipChunks = new java.util.ArrayList<>();
    private String currentUiAlias = "";
    private String currentCommand = "";
    
    /**
     * 处理打开UI指令
     */
    private void handleOpenUi(JsonObject json) {
        String alias = json.get("alias").getAsString();
        String command = json.get("command").getAsString();
        String md5 = json.has("md5") ? json.get("md5").getAsString() : "";
        
        LOGGER.info("收到打开UI指令: alias={}, command={}, md5={}", alias, command, md5);
        
        // 保存当前UI信息，等待ZIP数据传输完成
        currentUiAlias = alias;
        currentCommand = command;
        zipChunks.clear();
        
        // 检查缓存
        if (!md5.isEmpty() && MGuiResourcePackManager.getInstance().isCacheValid(alias, md5)) {
            // 缓存有效，直接打开UI，跳过下载
            LOGGER.info("缓存有效，直接打开UI: {}", alias);
            showToast("缓存命中", "使用本地缓存打开UI", ToastType.SUCCESS);
            openUiDirectly(alias, command);
            return;
        }
        
        // 缓存无效或没有MD5，开始下载
        startDownloadProgress();
    }
    
    /**
     * 处理ZIP数据块
     */
    private void handleZipChunk(JsonObject json) {
        int chunkIndex = json.get("chunkIndex").getAsInt();
        int totalChunks = json.get("totalChunks").getAsInt();
        String base64Data = json.get("data").getAsString();
        
        // 解码Base64数据
        byte[] chunkData = java.util.Base64.getDecoder().decode(base64Data);
        
        // 确保列表大小足够
        while (zipChunks.size() <= chunkIndex) {
            zipChunks.add(null);
        }
        zipChunks.set(chunkIndex, chunkData);
        
        // 更新下载进度
        updateDownloadProgress(chunkIndex + 1, totalChunks);
        
        LOGGER.debug("接收ZIP数据块: {}/{}", chunkIndex + 1, totalChunks);
    }
    
    /**
     * 处理ZIP数据传输完成
     */
    private void handleZipComplete() {
        LOGGER.info("ZIP数据传输完成，共 {} 块", zipChunks.size());
        
        // 更新进度为完成
        finishDownloadProgress();
        
        // 合并所有数据块
        int totalSize = zipChunks.stream().mapToInt(chunk -> chunk.length).sum();
        byte[] zipData = new byte[totalSize];
        int offset = 0;
        for (byte[] chunk : zipChunks) {
            System.arraycopy(chunk, 0, zipData, offset, chunk.length);
            offset += chunk.length;
        }
        
        // 解压并显示UI
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            try {
                // 先截取游戏画面
                ScreenshotManager.getInstance().captureScreenshot(() -> {
                    // 解压ZIP到缓存目录
                    MGuiResourcePackManager.getInstance().extractZipToCache(currentUiAlias, zipData);
                    
                    // 打开UI屏幕
                    MGuiHtmlScreen.openScreen(currentUiAlias, currentCommand);
                    // 显示成功Toast
                    showToast("UI加载完成", "已成功加载 " + currentUiAlias, ToastType.SUCCESS);
                });
            } catch (Exception e) {
                LOGGER.error("处理ZIP数据失败: {}", e.getMessage(), e);
                showToast("加载失败", "无法加载UI资源: " + e.getMessage(), ToastType.ERROR);
            }
        });
        
        // 清空缓冲区
        zipChunks.clear();
    }
    
    /**
     * 处理关闭UI指令（同时关闭 Minecraft 屏幕和 GUI.exe 进程）
     */
    private void handleCloseUi() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            // 关闭 Minecraft 屏幕
            if (client.screen instanceof MGuiHtmlScreen) {
                client.setScreen(null);
            }
            
            // 关闭 GUI.exe 进程
            try {
                MGuiWebViewManager webViewManager = MGuiWebViewManager.getInstance();
                webViewManager.closeUi("all");
                LOGGER.info("UI已关闭（包含 GUI.exe 进程）");
            } catch (Exception e) {
                LOGGER.warn("关闭 GUI.exe 进程失败: {}", e.getMessage());
            }
        });
    }
    
    /**
     * 处理直接打开UI指令（版本2）
     * 参数格式: gui.exe <url> <resolution> <width> <height>
     * 分辨率等级：1=全屏幕 2=自定义大小 3=正常预设 49=保持安静
     */
    private void handleOpenUiDirect(JsonObject json) {
        String url = json.get("url").getAsString();
        String resolution = json.get("resolution").getAsString();
        String width = json.has("width") ? json.get("width").getAsString() : "0";
        String height = json.has("height") ? json.get("height").getAsString() : "0";
        
        LOGGER.info("收到直接打开UI指令: url={}, resolution={}, width={}, height={}", 
            url, resolution, width, height);
        LOGGER.info("原始JSON数据: {}", json.toString());
        
        // 验证参数
        if (url.isEmpty()) {
            LOGGER.error("URL参数为空");
            return;
        }
        
        // 使用 MGuiWebViewManager 直接打开 URL
        try {
            MGuiWebViewManager webViewManager = MGuiWebViewManager.getInstance();
            webViewManager.openUiDirect(url, resolution, width, height);
            
            // 显示成功Toast
            showToast("UI打开成功", "已打开UI", ToastType.SUCCESS);
        } catch (Exception e) {
            LOGGER.error("直接打开UI失败: {}", e.getMessage(), e);
            showToast("打开失败", "无法打开UI: " + e.getMessage(), ToastType.ERROR);
        }
    }
    
    /**
     * 处理UI注册通知
     */
    private void handleUiRegistered(JsonObject json) {
        String command = json.get("command").getAsString();
        String alias = json.get("alias").getAsString();
        String description = json.get("description").getAsString();
        
        LOGGER.info("服务器注册了新UI: {} - {} ({})", command, description, alias);
    }
    
    /**
     * 注册事件
     */
    private void registerEvents() {
        // 客户端连接事件
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LOGGER.info("已连接到服务器，MGUI系统就绪");
        });
        
        // 客户端断开事件
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LOGGER.info("已断开服务器连接");
        });
        
        // 客户端停止事件
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("MGUI客户端正在关闭...");
            ScreenshotManager.getInstance().cleanup();
            MGuiResourcePackManager.getInstance().cleanup();
            MGuiWebViewManager.getInstance().cleanup();
        });
    }
    
    /**
     * 注册渲染事件
     */
    private void registerRenderEvents() {
        // 注册HUD渲染事件（只绘制进度条）
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> renderProgressBar(guiGraphics));
        
        // 注册客户端Tick事件（WebView检测）
        ClientTickEvents.END_CLIENT_TICK.register(this::checkWebViewClosed);
    }
    
    /**
     * 注册快捷键
     */
    private void registerKeyBindings() {
        clearCacheKey = new KeyMapping(
            "key.mgui.clear_cache",
            GLFW.GLFW_KEY_P,
            "category.mgui"
        );
        KeyBindingHelper.registerKeyBinding(clearCacheKey);
        
        // 注册按键事件
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (clearCacheKey.consumeClick()) {
                // 检查是否同时按下Ctrl+Alt（使用GLFW检测）
                long window = client.getWindow().getWindow();
                boolean ctrlPressed = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                                      org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                boolean altPressed = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                                     org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                if (ctrlPressed && altPressed) {
                    showClearCacheConfirm();
                }
            }
        });
    }
    
    /**
     * 开始下载进度
     */
    private void startDownloadProgress() {
        isDownloading = true;
        downloadProgress = 0;
        downloadTotal = 0;
        showToast("开始下载", "正在获取UI资源...", ToastType.INFO);
    }
    
    /**
     * 更新下载进度
     */
    private void updateDownloadProgress(int progress, int total) {
        downloadProgress = progress;
        downloadTotal = total;
    }
    
    /**
     * 完成下载进度
     */
    private void finishDownloadProgress() {
        isDownloading = false;
        downloadProgress = downloadTotal;
        showToast("下载完成", "正在解压UI资源...", ToastType.SUCCESS);
    }
    
    /**
     * 渲染下载进度条（顶部全屏）
     */
    private void renderProgressBar(GuiGraphics guiGraphics) {
        if (!isDownloading) {
            return;
        }
        
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        
        // 绘制背景（半透明）
        guiGraphics.fill(0, 0, screenWidth, 5, 0x88333333);
        
        // 绘制进度条（红色）
        if (downloadTotal > 0) {
            int progressWidth = (int) ((downloadProgress / (float) downloadTotal) * screenWidth);
            guiGraphics.fill(0, 0, progressWidth, 5, 0xFFFF0000);
        }
    }
    
    /**
     * 检查WebView是否关闭
     */
    private void checkWebViewClosed(Minecraft client) {
        if (client.screen instanceof MGuiHtmlScreen) {
            MGuiWebViewManager webViewManager = MGuiWebViewManager.getInstance();
            String uiId = ((MGuiHtmlScreen) client.screen).getUiId();
            if (uiId != null && !webViewManager.isUiOpen(uiId)) {
                // WebView已关闭，自动返回游戏
                client.execute(() -> {
                    client.setScreen(null);
                    showToast("UI已关闭", "WebView窗口已关闭", ToastType.INFO);
                });
            }
        }
    }
    
    /**
     * 显示清理缓存确认弹窗
     */
    private void showClearCacheConfirm() {
        Minecraft client = Minecraft.getInstance();
        ConfirmScreen confirmScreen = new ConfirmScreen(
            confirmed -> {
                // 无论确认还是取消，都销毁对话框
                client.setScreen(null);
                if (confirmed) {
                    clearAllResourcePacks();
                }
            },
            Component.literal("确认清理资源包"),
            Component.literal("确定要清除所有已下载的资源包吗？此操作不可恢复。")
        );
        client.setScreen(confirmScreen);
    }
    
    /**
     * 清除所有已下载的资源包
     */
    private void clearAllResourcePacks() {
        try {
            MGuiResourcePackManager resourcePackManager = MGuiResourcePackManager.getInstance();
            resourcePackManager.clearAllCache();
            showToast("清理完成", "所有资源包已清除", ToastType.SUCCESS);
        } catch (Exception e) {
            showToast("清理失败", "无法清除资源包: " + e.getMessage(), ToastType.ERROR);
        }
    }
    
    /**
     * 显示消息通知（使用 msgsrv.exe）
     */
    public void showToast(String title, String message, ToastType type) {
        // 在新线程中显示通知，避免阻塞游戏主线程
        new Thread(() -> {
            try {
                // 使用 msgsrv.exe 显示通知
                if (showToastWithMsgSrv(title, message)) {
                    return;
                }
                
                // msgsrv.exe 失败时使用游戏内消息
                LOGGER.debug("msgsrv.exe 不可用，将使用游戏内消息");
                showInGameMessage(title, message, type);
                
            } catch (Exception e) {
                LOGGER.debug("显示通知异常: {}，将使用游戏内消息", e.getMessage());
                showInGameMessage(title, message, type);
            }
        }, "MGUI-Toast-Notification").start();
        
        LOGGER.info("Toast: {} - {}", title, message);
    }
    
    /**
     * 使用 msgsrv.exe 显示通知（简化版）
     * @return true 表示成功，false 表示失败
     */
    private boolean showToastWithMsgSrv(String title, String message) {
        try {
            java.io.File msgsrvExe = new java.io.File("gui/msgsrv.exe");
            
            if (!msgsrvExe.exists()) {
                LOGGER.debug("msgsrv.exe 不存在: {}", msgsrvExe.getAbsolutePath());
                return false;
            }
            
            ProcessBuilder processBuilder = new ProcessBuilder(
                msgsrvExe.getAbsolutePath(),
                title + ": " + message
            );
            processBuilder.directory(new java.io.File("gui/"));
            processBuilder.start();
            
            LOGGER.debug("msgsrv.exe 已启动: {}", title + ": " + message);
            return true;
            
        } catch (Exception e) {
            LOGGER.debug("启动 msgsrv.exe 失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 使用游戏内聊天消息显示通知（备用方案）
     */
    private void showInGameMessage(String title, String message, ToastType type) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            LOGGER.debug("无法显示游戏内消息：玩家为空");
            return;
        }
        
        // 根据类型设置不同的颜色
        String colorCode = switch (type) {
            case SUCCESS -> "§a"; // 绿色
            case ERROR -> "§c";   // 红色
            default -> "§e";      // 黄色
        };
        
        // 显示客户端消息
        client.execute(() -> {
            client.player.displayClientMessage(Component.literal(
                colorCode + "[MGUI] " + title + ": " + message
            ), false);
        });
    }
    
    /**
     * 直接打开UI（缓存命中时使用）
     */
    private void openUiDirectly(String alias, String command) {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> {
                try {
                    // 先截取游戏画面
                    ScreenshotManager.getInstance().captureScreenshot(() -> {
                        // 直接打开UI屏幕（不需要解压）
                        Minecraft mc = Minecraft.getInstance();
                        if (mc != null) {
                            mc.execute(() -> {
                                MGuiHtmlScreen.openScreen(alias, command);
                            });
                        }
                    });
                } catch (Exception e) {
                    LOGGER.error("打开UI失败: {}", e.getMessage(), e);
                    showToast("打开失败", "无法打开UI: " + e.getMessage(), ToastType.ERROR);
                }
            });
        }
    }
    
    /**
     * Toast类型枚举
     */
    public enum ToastType {
        SUCCESS,
        ERROR,
        INFO
    }
}