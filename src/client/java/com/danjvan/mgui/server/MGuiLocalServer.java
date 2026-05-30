package com.danjvan.mgui.server;

import com.danjvan.mgui.util.MGuiLogger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class MGuiLocalServer {
    private static MGuiLocalServer instance;
    private HttpServer server;
    private final int port = 27890;
    private final Map<String, byte[]> cachedImages = new HashMap<>();
    
    private MGuiLocalServer() {
    }
    
    public static MGuiLocalServer getInstance() {
        if (instance == null) {
            instance = new MGuiLocalServer();
        }
        return instance;
    }
    
    /**
     * 启动本地服务器
     */
    public void start() {
        new Thread(() -> {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
                
                // 注册处理器
                server.createContext("/bg.png", new BackgroundImageHandler());
                server.createContext("/res/", new ResourceHandler());
                
                server.setExecutor(null); // 使用默认线程池
                server.start();
                
                MGuiLogger.info("MGUI 本地服务器已启动: http://127.0.0.1:" + port);
                
            } catch (IOException e) {
                MGuiLogger.error("启动本地服务器失败: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            MGuiLogger.info("MGUI 本地服务器已停止");
        }
    }
    
    /**
     * 缓存背景图
     */
    public void cacheBackgroundImage(String key, byte[] imageData) {
        cachedImages.put(key, imageData);
        MGuiLogger.debug("缓存背景图: " + key);
    }
    
    /**
     * 背景图处理器
     */
    private class BackgroundImageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            
            if (query != null && cachedImages.containsKey(query)) {
                byte[] imageData = cachedImages.get(query);
                
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(imageData.length));
                exchange.sendResponseHeaders(200, imageData.length);
                
                OutputStream os = exchange.getResponseBody();
                os.write(imageData);
                os.close();
            } else {
                // 返回空图像或默认图像
                byte[] emptyImage = new byte[0];
                exchange.sendResponseHeaders(404, emptyImage.length);
                exchange.getResponseBody().close();
            }
        }
    }
    
    /**
     * 资源文件处理器
     */
    private class ResourceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // 从 MGUI 目录读取文件
            Path filePath = Paths.get("mc/1.21.8/mgui" + path.replace("/res", ""));
            
            if (Files.exists(filePath)) {
                byte[] fileData = Files.readAllBytes(filePath);
                
                String contentType = getContentType(filePath.toString());
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileData.length));
                exchange.sendResponseHeaders(200, fileData.length);
                
                OutputStream os = exchange.getResponseBody();
                os.write(fileData);
                os.close();
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.getResponseBody().close();
            }
        }
        
        private String getContentType(String fileName) {
            if (fileName.endsWith(".json")) return "application/json";
            if (fileName.endsWith(".html")) return "text/html";
            if (fileName.endsWith(".png")) return "image/png";
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
            if (fileName.endsWith(".css")) return "text/css";
            if (fileName.endsWith(".js")) return "application/javascript";
            return "application/octet-stream";
        }
    }
}
