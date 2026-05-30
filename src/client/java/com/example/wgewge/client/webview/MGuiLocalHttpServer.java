package com.example.wgewge.client.webview;

import com.example.wgewge.MGuiConstants;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * MGUI 本地 HTTP 服务器
 * 用于提供 HTML 资源和处理 UI 指令
 */
public class MGuiLocalHttpServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MGuiLocalHttpServer.class);
    
    private HttpServer server;
    private final int port;
    
    // 缓存的 UI 数据
    private final Map<String, byte[]> cachedData = new HashMap<>();
    
    public MGuiLocalHttpServer(int port) {
        this.port = port;
    }
    
    /**
     * 启动服务器
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        
        // 注册处理器
        server.createContext("/ui/", new UiResourceHandler());
        server.createContext("/api/", new ApiHandler());
        
        server.setExecutor(null); // 使用默认线程池
        server.start();
        
        LOGGER.info("MGUI HTTP 服务器已启动: http://127.0.0.1:{}", port);
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            LOGGER.info("MGUI HTTP 服务器已停止");
        }
    }
    
    /**
     * 缓存数据
     */
    public void cacheData(String key, byte[] data) {
        cachedData.put(key, data);
        LOGGER.debug("缓存数据: {} ({} bytes)", key, data.length);
    }
    
    /**
     * UI 资源处理器 - 提供 HTML/CSS/JS 等资源
     */
    private class UiResourceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            try {
                // 从 MGUI 资源目录读取文件
                // 路径格式: /ui/{uiId}/{filename}
                String[] parts = path.split("/");
                if (parts.length < 3) {
                    sendError(exchange, 400, "Invalid path");
                    return;
                }
                
                String uiId = parts[2];
                String filename = parts.length > 3 ? String.join("/", java.util.Arrays.copyOfRange(parts, 3, parts.length)) : "";
                
                // 构建文件路径
                Path filePath = Paths.get(MGuiConstants.RESOURCE_PACK_BASE_PATH, uiId, filename);
                
                if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                    byte[] fileData = Files.readAllBytes(filePath);
                    
                    String contentType = getContentType(filename);
                    exchange.getResponseHeaders().set("Content-Type", contentType);
                    exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileData.length));
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, fileData.length);
                    
                    OutputStream os = exchange.getResponseBody();
                    os.write(fileData);
                    os.close();
                    
                    LOGGER.debug("提供资源: {}", path);
                } else {
                    sendError(exchange, 404, "File not found: " + path);
                }
                
            } catch (Exception e) {
                LOGGER.error("处理请求失败: {}", e.getMessage(), e);
                sendError(exchange, 500, "Internal server error");
            }
        }
        
        private String getContentType(String fileName) {
            if (fileName.endsWith(".html")) return "text/html; charset=utf-8";
            if (fileName.endsWith(".css")) return "text/css; charset=utf-8";
            if (fileName.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (fileName.endsWith(".json")) return "application/json; charset=utf-8";
            if (fileName.endsWith(".png")) return "image/png";
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
            if (fileName.endsWith(".gif")) return "image/gif";
            if (fileName.endsWith(".svg")) return "image/svg+xml";
            if (fileName.endsWith(".woff")) return "font/woff";
            if (fileName.endsWith(".woff2")) return "font/woff2";
            if (fileName.endsWith(".ttf")) return "font/ttf";
            return "application/octet-stream";
        }
    }
    
    /**
     * API 处理器 - 处理 UI 指令和消息
     */
    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            
            try {
                // 处理 OPTIONS 预检请求
                if ("OPTIONS".equalsIgnoreCase(method)) {
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                
                if ("POST".equalsIgnoreCase(method)) {
                    // 处理 POST 请求（UI 发送的指令）
                    handlePostRequest(exchange, path);
                } else if ("GET".equalsIgnoreCase(method)) {
                    // 处理 GET 请求
                    handleGetRequest(exchange, path);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
                
            } catch (Exception e) {
                LOGGER.error("处理 API 请求失败: {}", e.getMessage(), e);
                sendError(exchange, 500, "Internal server error");
            }
        }
        
        private void handlePostRequest(HttpExchange exchange, String path) throws IOException {
            // 读取请求体
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            String body = new String(requestBody, java.nio.charset.StandardCharsets.UTF_8);
            
            LOGGER.info("收到 API 请求: {} - {}", path, body);
            
            String response;
            
            try {
                // 解析 JSON
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                
                // 根据不同的 API 路径处理不同的指令
                if (path.equals("/api/command") || path.equals("/api/command/")) {
                    // 执行 Minecraft 命令
                    String command = json.has("command") ? json.get("command").getAsString() : "";
                    
                    if (!command.isEmpty()) {
                        // 获取 Minecraft 客户端实例
                        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                        
                        // 在游戏主线程直接执行命令（模拟玩家输入）
                        minecraft.execute(() -> {
                            try {
                                // 移除可能存在的 / 前缀（ServerboundChatCommandPacket 不需要 /）
                                String cmdWithoutSlash = command.startsWith("/") ? command.substring(1) : command;
                                
                                // 通过聊天系统执行命令（客户端和单人都适用）
                                if (minecraft.getConnection() != null && minecraft.player != null) {
                                    // 发送聊天命令包（不需要 / 前缀）
                                    minecraft.getConnection().send(
                                        new net.minecraft.network.protocol.game.ServerboundChatCommandPacket(cmdWithoutSlash)
                                    );
                                    LOGGER.info("执行命令: {}", cmdWithoutSlash);
                                } else {
                                    LOGGER.warn("无法执行命令：玩家或连接不存在");
                                }
                            } catch (Exception e) {
                                LOGGER.error("执行命令失败: {}", e.getMessage(), e);
                            }
                        });
                        
                        response = "{\"status\":\"ok\",\"message\":\"Command executed: " + command + "\"}";
                    } else {
                        response = "{\"status\":\"error\",\"message\":\"Empty command\"}";
                    }
                    
                } else if (path.equals("/api/close") || path.equals("/api/close/")) {
                    // 关闭 UI
                    LOGGER.info("收到关闭 UI 请求");
                    
                    net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                    minecraft.execute(() -> {
                        if (minecraft.screen != null) {
                            minecraft.setScreen(null);
                        }
                    });
                    
                    response = "{\"status\":\"ok\",\"message\":\"UI closing\"}";
                    
                } else if (path.equals("/api/message") || path.equals("/api/message/")) {
                    // 发送聊天消息
                    String message = json.has("message") ? json.get("message").getAsString() : "";
                    
                    if (!message.isEmpty()) {
                        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                        minecraft.execute(() -> {
                            if (minecraft.player != null) {
                                minecraft.player.displayClientMessage(
                                    net.minecraft.network.chat.Component.literal(message), 
                                    false
                                );
                            }
                        });
                        
                        response = "{\"status\":\"ok\",\"message\":\"Message sent\"}";
                    } else {
                        response = "{\"status\":\"error\",\"message\":\"Empty message\"}";
                    }
                    
                } else {
                    response = "{\"status\":\"error\",\"message\":\"Unknown API endpoint\"}";
                }
                
            } catch (Exception e) {
                LOGGER.error("处理请求失败: {}", e.getMessage(), e);
                response = "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
            }
            
            // 返回响应
            byte[] responseData = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(200, responseData.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(responseData);
            os.close();
        }
        
        private void handleGetRequest(HttpExchange exchange, String path) throws IOException {
            // 简单的 GET 请求处理
            String response = "{\"status\":\"ok\",\"message\":\"MGUI API Server\",\"port\":" + port + "}";
            byte[] responseData = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, responseData.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(responseData);
            os.close();
        }
    }
    
    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        byte[] responseData = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(code, responseData.length);
        
        OutputStream os = exchange.getResponseBody();
        os.write(responseData);
        os.close();
    }
}
