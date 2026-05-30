package com.example.wgewge;

import net.minecraft.server.MinecraftServer;

/**
 * MGUI 常量定义
 */
public class MGuiConstants {
    public static final String MOD_ID = "mgui";
    public static final String MOD_NAME = "Minecraft GUI System";
    public static final String VERSION = "1.0.0";
    
    // 网络通道ID
    public static final String UI_REGISTER_CHANNEL = "mgui:ui_register";
    public static final String SERVER_TO_CLIENT_CHANNEL = "mgui:s2c";
    public static final String CLIENT_TO_SERVER_CHANNEL = "mgui:c2s";
    public static final String CLIENT_REQUEST_CHANNEL = "mgui:client_request";
    
    // 资源包相关
    public static final String RESOURCE_PACK_BASE_PATH = "mgui/resourcepacks/";
    public static final String SCREENSHOT_CACHE_PATH = "mgui/screenshots/";
    
    private static MinecraftServer server;
    
    /**
     * 设置服务器实例
     */
    public static void setServer(MinecraftServer serverInstance) {
        server = serverInstance;
    }
    
    /**
     * 获取服务器实例
     */
    public static MinecraftServer getServer() {
        return server;
    }
    
    /**
     * 创建网络缓冲区
     */
    public static net.minecraft.network.FriendlyByteBuf createBuffer(String data) {
        io.netty.buffer.UnpooledByteBufAllocator allocator = new io.netty.buffer.UnpooledByteBufAllocator(false);
        io.netty.buffer.ByteBuf byteBuf = allocator.buffer();
        byteBuf.writeBytes(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new net.minecraft.network.FriendlyByteBuf(byteBuf);
    }
}
