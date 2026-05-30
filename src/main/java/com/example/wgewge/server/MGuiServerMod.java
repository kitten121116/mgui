package com.example.wgewge.server;

import com.example.wgewge.MGuiConstants;
import com.example.wgewge.network.MGuiNetworkAdapter;
import com.example.wgewge.network.MGuiPacket;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * MGUI 服务器端模组
 * 负责：
 * 1. 接收业务mod的UI资源注册
 * 2. 管理命令与UI资源的映射关系
 * 3. 向客户端发送UI打开指令
 * 4. 处理客户端的请求和响应
 */
public class MGuiServerMod implements DedicatedServerModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(MGuiConstants.MOD_ID + "_server");
    
    // 命令到UI资源包的映射 <command, UiResourceInfo>
    private static final Map<String, UiResourceInfo> commandUiMap = new HashMap<>();
    
    // 资源包根目录
    private static final String RESOURCE_PACK_DIR = "mguiregs/";
    
    // 注册信息保存文件
    private static final String REGISTRATION_FILE = "mguiregs/registrations.json";
    
    @Override
    public void onInitializeServer() {
        LOGGER.info("MGUI 服务器端模组初始化...");
        
        // 创建资源目录
        ensureResourceDir();
        
        // 加载已保存的注册信息
        loadRegistrations();
        
        // 注册网络通道
        registerNetworkChannels();
        
        // 注册命令
        registerCommands();
        
        // 注册玩家连接事件
        registerPlayerEvents();
        
        LOGGER.info("MGUI 服务器端模组初始化完成");
    }
    
    /**
     * 注册网络通信通道
     */
    private void registerNetworkChannels() {
        // 注册接收来自业务mod的UI注册消息
        ServerPlayNetworking.registerGlobalReceiver(
            MGuiPacket.UiRegisterPacket.TYPE,
            (payload, context) -> {
                String jsonData = ((MGuiPacket.UiRegisterPacket) payload).getData();
                context.player().getServer().execute(() -> handleUiRegistration(context.player(), jsonData));
            }
        );
        
        // 注册接收客户端的截图请求等
        ServerPlayNetworking.registerGlobalReceiver(
            MGuiPacket.ClientRequestPacket.TYPE,
            (payload, context) -> {
                String requestData = ((MGuiPacket.ClientRequestPacket) payload).getData();
                context.player().getServer().execute(() -> handleClientRequest(context.player(), requestData));
            }
        );
        
        LOGGER.info("网络通道注册完成");
    }
    
    /**
     * 处理UI资源注册（来自业务mod）
     * 业务mod需要先解压资源包到 mguiregs/ 目录，然后调用此方法注册
     */
    private void handleUiRegistration(ServerPlayer player, String jsonData) {
        try {
            JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
            
            String command = json.get("command").getAsString();
            String zipFileName = json.get("zipFileName").getAsString();  // ZIP文件名
            String alias = json.get("alias").getAsString();              // 别名（简称）
            String description = json.has("description") ? json.get("description").getAsString() : "";
            
            // 验证ZIP文件是否存在
            String zipPath = RESOURCE_PACK_DIR + zipFileName;
            java.io.File zipFile = new java.io.File(zipPath);
            if (!zipFile.exists()) {
                LOGGER.error("资源包文件不存在: {}", zipPath);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§c资源包文件不存在: " + zipFileName));
                return;
            }
            
            UiResourceInfo uiInfo = new UiResourceInfo(alias, command, zipFileName, description);
            commandUiMap.put(command, uiInfo);
            
            // 保存注册信息
            saveRegistrations();
            
            LOGGER.info("UI资源注册成功: command={}, alias={}, zip={}", command, alias, zipFileName);
            
            // 向请求的玩家发送注册确认
            broadcastUiRegistration(uiInfo, player);
            
        } catch (Exception e) {
            LOGGER.error("处理UI注册失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 处理客户端请求
     */
    private void handleClientRequest(ServerPlayer player, String requestData) {
        try {
            JsonObject json = JsonParser.parseString(requestData).getAsJsonObject();
            String requestType = json.get("type").getAsString();
            
            switch (requestType) {
                case "screenshot_ready":
                    LOGGER.info("客户端截图准备就绪: {}", player.getName().getString());
                    break;
                case "ui_closed":
                    LOGGER.info("客户端关闭UI: {}", player.getName().getString());
                    break;
                default:
                    LOGGER.warn("未知的请求类型: {}", requestType);
            }
        } catch (Exception e) {
            LOGGER.error("处理客户端请求失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 注册命令
     */
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // 注册 /mgui_open 命令 - 强制打开指定UI
            dispatcher.register(
                net.minecraft.commands.Commands.literal("mgui_open")
                    .requires(source -> source.hasPermission(2))
                    .then(net.minecraft.commands.Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                        .then(net.minecraft.commands.Commands.argument("uiCommand", ResourceLocationArgument.id())
                            .executes(context -> {
                                ServerPlayer targetPlayer = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
                                String uiCommand = context.getArgument("uiCommand", ResourceLocation.class).toString();
                                return openUiForPlayer(targetPlayer, uiCommand);
                            })
                        )
                    )
            );
                
            // 注册 /mgui_open2 命令 - 强制打开玩家的UI，直接构建参数
            // 用法: /mgui_open2 <player> <resolution> <width> <height> <x> <y> <url>
            // 分辨率等级: 1=全屏 2=自定义大小 3=正常预设 49=保持安静
            dispatcher.register(
                Commands.literal("mgui_open2")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("resolution", StringArgumentType.word())
                            .then(Commands.argument("width", StringArgumentType.word())
                                .then(Commands.argument("height", StringArgumentType.word())
                                    .then(Commands.argument("x", StringArgumentType.word())
                                        .then(Commands.argument("y", StringArgumentType.word())
                                            .then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
                                                    String resolution = StringArgumentType.getString(context, "resolution");
                                                    String width = StringArgumentType.getString(context, "width");
                                                    String height = StringArgumentType.getString(context, "height");
                                                    String x = StringArgumentType.getString(context, "x");
                                                    String y = StringArgumentType.getString(context, "y");
                                                    String url = StringArgumentType.getString(context, "url");
                                                    return openUiDirectForPlayer(targetPlayer, url, resolution, width, height, x, y);
                                                })
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
            );
            
            // 注册 /mgui_close 命令 - 强制关闭指定玩家的所有UI
            dispatcher.register(
                Commands.literal("mgui_close")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
                            return closeUiForPlayer(targetPlayer);
                        })
                    )
            );
                
            // 注册 /mgui_list 命令 - 列出所有注册的UI
            dispatcher.register(
                net.minecraft.commands.Commands.literal("mgui_list")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> {
                        listRegisteredUis(context.getSource().getPlayerOrException());
                        return 1;
                    })
            );
                
            // 注册 /guireg 命令 - 手动注册UI（用于调试）
            // 用法: /guireg <zipFileName> <alias> [description]
            dispatcher.register(
                Commands.literal("guireg")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("zipFileName", StringArgumentType.word())
                        .then(Commands.argument("alias", StringArgumentType.word())
                            .executes(context -> {
                                String zipFileName = StringArgumentType.getString(context, "zipFileName");
                                String alias = StringArgumentType.getString(context, "alias");
                                return manualRegisterUi(context.getSource().getPlayerOrException(), zipFileName, alias, "");
                            })
                            .then(Commands.argument("description", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String zipFileName = StringArgumentType.getString(context, "zipFileName");
                                    String alias = StringArgumentType.getString(context, "alias");
                                    String description = StringArgumentType.getString(context, "description");
                                    return manualRegisterUi(context.getSource().getPlayerOrException(), zipFileName, alias, description);
                                })
                            )
                        )
                    )
            );
                
            // 注册 /regdel 命令 - 删除已注册的UI
            // 用法: /regdel <alias>
            dispatcher.register(
                Commands.literal("regdel")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("alias", StringArgumentType.word())
                        .executes(context -> {
                            String alias = StringArgumentType.getString(context, "alias");
                            return unregisterUi(context.getSource().getPlayerOrException(), alias);
                        })
                    )
            );
            
            // 注册 /guireg_url 命令 - 通过URL注册UI资源
            // 用法: /guireg_url <alias> <url> [resolution] [width] [height] [x] [y] [description]
            // 分辨率等级: 1=全屏 2=自定义大小 3=正常预设 49=保持安静
            dispatcher.register(
                Commands.literal("guireg_url")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("alias", StringArgumentType.word())
                        .then(Commands.argument("url", StringArgumentType.greedyString())
                            .executes(context -> {
                                String alias = StringArgumentType.getString(context, "alias");
                                String url = StringArgumentType.getString(context, "url");
                                return registerUiByUrl(context.getSource().getPlayerOrException(), alias, url, "3", "0", "0", "0", "0", "");
                            })
                            .then(Commands.argument("resolution", StringArgumentType.word())
                                .executes(context -> {
                                    String alias = StringArgumentType.getString(context, "alias");
                                    String url = StringArgumentType.getString(context, "url");
                                    String resolution = StringArgumentType.getString(context, "resolution");
                                    return registerUiByUrl(context.getSource().getPlayerOrException(), alias, url, resolution, "0", "0", "0", "0", "");
                                })
                                .then(Commands.argument("width", StringArgumentType.word())
                                    .executes(context -> {
                                        String alias = StringArgumentType.getString(context, "alias");
                                        String url = StringArgumentType.getString(context, "url");
                                        String resolution = StringArgumentType.getString(context, "resolution");
                                        String width = StringArgumentType.getString(context, "width");
                                        return registerUiByUrl(context.getSource().getPlayerOrException(), alias, url, resolution, width, "0", "0", "0", "");
                                    })
                                    .then(Commands.argument("height", StringArgumentType.word())
                                        .executes(context -> {
                                            String alias = StringArgumentType.getString(context, "alias");
                                            String url = StringArgumentType.getString(context, "url");
                                            String resolution = StringArgumentType.getString(context, "resolution");
                                            String width = StringArgumentType.getString(context, "width");
                                            String height = StringArgumentType.getString(context, "height");
                                            return registerUiByUrl(context.getSource().getPlayerOrException(), alias, url, resolution, width, height, "0", "0", "");
                                        })
                                        .then(Commands.argument("x", StringArgumentType.word())
                                            .executes(context -> {
                                                String alias = StringArgumentType.getString(context, "alias");
                                                String url = StringArgumentType.getString(context, "url");
                                                String resolution = StringArgumentType.getString(context, "resolution");
                                                String width = StringArgumentType.getString(context, "width");
                                                String height = StringArgumentType.getString(context, "height");
                                                String x = StringArgumentType.getString(context, "x");
                                                return registerUiByUrl(context.getSource().getPlayerOrException(), alias, url, resolution, width, height, x, "0", "");
                                            })
                                            .then(Commands.argument("y", StringArgumentType.word())
                                                .executes(context -> {
                                                    String alias = StringArgumentType.getString(context, "alias");
                                                    String url = StringArgumentType.getString(context, "url");
                                                    String resolution = StringArgumentType.getString(context, "resolution");
                                                    String width = StringArgumentType.getString(context, "width");
                                                    String height = StringArgumentType.getString(context, "height");
                                                    String x = StringArgumentType.getString(context, "x");
                                                    String y = StringArgumentType.getString(context, "y");
                                                    return registerUiByUrl(context.getSource().getPlayerOrException(), alias, url, resolution, width, height, x, y, "");
                                                })
                                                .then(Commands.argument("description", StringArgumentType.greedyString())
                                                    .executes(context -> {
                                                        String alias = StringArgumentType.getString(context, "alias");
                                                        String url = StringArgumentType.getString(context, "url");
                                                        String resolution = StringArgumentType.getString(context, "resolution");
                                                        String width = StringArgumentType.getString(context, "width");
                                                        String height = StringArgumentType.getString(context, "height");
                                                        String x = StringArgumentType.getString(context, "x");
                                                        String y = StringArgumentType.getString(context, "y");
                                                        String description = StringArgumentType.getString(context, "description");
                                                        return registerUiByUrl(context.getSource().getPlayerOrException(), alias, url, resolution, width, height, x, y, description);
                                                    })
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
            );
        });
    }
    
    /**
     * 注册玩家事件
     */
    private void registerPlayerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String playerName = handler.getPlayer().getName().getString();
            String playerUuid = handler.getPlayer().getStringUUID();
            LOGGER.info("玩家连接: {} ({})", playerName, playerUuid);
        });
        
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            String playerName = handler.getPlayer().getName().getString();
            LOGGER.info("玩家断开: {}", playerName);
        });
    }
    
    /**
     * 计算字节数组的MD5值
     */
    private String calculateMd5(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            LOGGER.error("计算MD5失败", e);
            return "";
        }
    }
    
    /**
     * 为指定玩家打开UI（支持ZIP模式和URL模式）
     */
    public static int openUiForPlayer(ServerPlayer player, String command) {
        UiResourceInfo uiInfo = commandUiMap.get(command);
        if (uiInfo == null) {
            player.sendSystemMessage(Component.literal("§c未找到UI资源: " + command));
            return 0;
        }
        
        // 判断是URL模式还是ZIP模式
        if (!uiInfo.url().isEmpty()) {
            // URL模式 - 直接打开
            return openUiDirectForPlayer(player, uiInfo.url(), uiInfo.resolution(), 
                uiInfo.width(), uiInfo.height(), uiInfo.x(), uiInfo.y());
        }
        
        // ZIP模式 - 读取ZIP文件并发送给客户端
        try {
            String zipPath = RESOURCE_PACK_DIR + uiInfo.zipFileName();
            java.io.File zipFile = new java.io.File(zipPath);
            
            if (!zipFile.exists()) {
                player.sendSystemMessage(Component.literal(
                    "§c资源包文件不存在: " + uiInfo.zipFileName()));
                return 0;
            }
            
            // 读取ZIP文件内容
            byte[] zipData = java.nio.file.Files.readAllBytes(zipFile.toPath());
            
            // 计算MD5（用于客户端缓存校验）
            String md5 = new MGuiServerMod().calculateMd5(zipData);
            
            // 发送打开UI指令（包含元数据和MD5）
            JsonObject message = new JsonObject();
            message.addProperty("type", "open_ui");
            message.addProperty("alias", uiInfo.alias());
            message.addProperty("command", uiInfo.command());
            message.addProperty("zipFileName", uiInfo.zipFileName());
            message.addProperty("description", uiInfo.description());
            message.addProperty("md5", md5);
            
            // 先发送元数据（使用网络适配器，自动选择协议）
            MGuiNetworkAdapter.sendToClient(player, message);
            
            // 再发送ZIP数据（使用单独的消息）
            MGuiServerMod instance = new MGuiServerMod();
            instance.sendZipDataToClient(player, zipData);
            
            player.sendSystemMessage(Component.literal(
                "§a正在打开UI: §e" + uiInfo.description()));
            LOGGER.info("为玩家 {} 打开UI: {} ({}) MD5: {}", player.getName().getString(), command, uiInfo.alias(), md5);
            
            return 1;
            
        } catch (Exception e) {
            LOGGER.error("打开UI失败: {}", e.getMessage(), e);
            player.sendSystemMessage(Component.literal(
                "§c打开UI失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 广播UI注册信息
     */
    private void broadcastUiRegistration(UiResourceInfo uiInfo, ServerPlayer player) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "ui_registered");
        message.addProperty("command", uiInfo.command);
        message.addProperty("alias", uiInfo.alias);
        message.addProperty("description", uiInfo.description);
        
        // 发送给请求的玩家（使用网络适配器）
        if (player != null && player.connection != null) {
            MGuiNetworkAdapter.sendToClient(player, message);
        }
        
        // TODO: 如果需要广播给所有在线玩家，需要保存 MinecraftServer 引用
        // 目前只发送给请求者
    }
    
    /**
     * 发送ZIP数据到客户端
     */
    private void sendZipDataToClient(ServerPlayer player, byte[] zipData) {
        // 分块发送ZIP数据（避免包过大）
        int chunkSize = 32000; // 每块大小
        int totalChunks = (int) Math.ceil((double) zipData.length / chunkSize);
        
        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, zipData.length);
            byte[] chunk = java.util.Arrays.copyOfRange(zipData, start, end);
            
            JsonObject chunkMessage = new JsonObject();
            chunkMessage.addProperty("type", "zip_chunk");
            chunkMessage.addProperty("chunkIndex", i);
            chunkMessage.addProperty("totalChunks", totalChunks);
            chunkMessage.addProperty("data", java.util.Base64.getEncoder().encodeToString(chunk));
            
            MGuiNetworkAdapter.sendToClient(player, chunkMessage);
        }
        
        // 发送完成标记
        JsonObject completeMessage = new JsonObject();
        completeMessage.addProperty("type", "zip_complete");
        MGuiNetworkAdapter.sendToClient(player, completeMessage);
    }
    
    /**
     * 确保资源目录存在
     */
    private void ensureResourceDir() {
        java.nio.file.Path dirPath = java.nio.file.Paths.get(RESOURCE_PACK_DIR);
        if (!java.nio.file.Files.exists(dirPath)) {
            try {
                java.nio.file.Files.createDirectories(dirPath);
                LOGGER.info("创建资源目录: {}", RESOURCE_PACK_DIR);
            } catch (java.io.IOException e) {
                LOGGER.error("创建资源目录失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 保存注册信息到文件
     */
    private void saveRegistrations() {
        try {
            String jsonString = new com.google.gson.Gson().toJson(createRegistrationJsonArray());
            java.nio.file.Files.writeString(
                java.nio.file.Paths.get(REGISTRATION_FILE),
                jsonString,
                java.nio.charset.StandardCharsets.UTF_8
            );
            
            LOGGER.info("已保存 {} 个UI注册信息", commandUiMap.size());
        } catch (Exception e) {
            LOGGER.error("保存注册信息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 创建注册信息的JSON数组
     */
    private com.google.gson.JsonArray createRegistrationJsonArray() {
        com.google.gson.JsonArray jsonArray = new com.google.gson.JsonArray();
        
        for (Map.Entry<String, UiResourceInfo> entry : commandUiMap.entrySet()) {
            UiResourceInfo info = entry.getValue();
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("alias", info.alias());
            json.addProperty("command", info.command());
            json.addProperty("zipFileName", info.zipFileName());
            json.addProperty("description", info.description());
            json.addProperty("url", info.url());
            json.addProperty("resolution", info.resolution());
            json.addProperty("x", info.x());
            json.addProperty("y", info.y());
            jsonArray.add(json);
        }
        
        return jsonArray;
    }
    
    /**
     * 从文件加载注册信息
     */
    private void loadRegistrations() {
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get(REGISTRATION_FILE);
            if (!java.nio.file.Files.exists(filePath)) {
                LOGGER.info("注册信息文件不存在，跳过加载");
                return;
            }
            
            String jsonString = java.nio.file.Files.readString(filePath, java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonArray jsonArray = com.google.gson.JsonParser.parseString(jsonString).getAsJsonArray();
            
            int count = 0;
            for (int i = 0; i < jsonArray.size(); i++) {
                com.google.gson.JsonObject json = jsonArray.get(i).getAsJsonObject();
                String alias = json.get("alias").getAsString();
                String command = json.get("command").getAsString();
                String zipFileName = json.has("zipFileName") ? json.get("zipFileName").getAsString() : "";
                String description = json.has("description") ? json.get("description").getAsString() : "";
                String url = json.has("url") ? json.get("url").getAsString() : "";
                String resolution = json.has("resolution") ? json.get("resolution").getAsString() : "2";
                String x = json.has("x") ? json.get("x").getAsString() : "1250";
                String y = json.has("y") ? json.get("y").getAsString() : "590";
                
                // 判断是URL模式还是ZIP模式
                if (!url.isEmpty()) {
                    // URL模式，直接注册
                    UiResourceInfo uiInfo = new UiResourceInfo(alias, command, zipFileName, description, url, resolution, x, y);
                    commandUiMap.put(command, uiInfo);
                    count++;
                    LOGGER.debug("加载URL UI注册: {} -> {}", command, alias);
                } else {
                    // ZIP模式，验证ZIP文件是否存在
                    java.nio.file.Path zipPath = java.nio.file.Paths.get(RESOURCE_PACK_DIR, zipFileName);
                    
                    if (java.nio.file.Files.exists(zipPath)) {
                        UiResourceInfo uiInfo = new UiResourceInfo(alias, command, zipFileName, description);
                        commandUiMap.put(command, uiInfo);
                        count++;
                        LOGGER.debug("加载ZIP UI注册: {} -> {}", command, alias);
                    } else {
                        LOGGER.warn("跳过不存在的资源包: {} ({})", zipFileName, command);
                    }
                }
            }
            
            LOGGER.info("已加载 {} 个UI注册信息", count);
        } catch (Exception e) {
            LOGGER.error("加载注册信息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 取消注册UI（通过/regdel命令）
     */
    private int unregisterUi(ServerPlayer player, String alias) {
        try {
            // 查找匹配的UI
            String targetCommand = null;
            UiResourceInfo targetInfo = null;
            
            for (Map.Entry<String, UiResourceInfo> entry : commandUiMap.entrySet()) {
                if (entry.getValue().alias.equals(alias)) {
                    targetCommand = entry.getKey();
                    targetInfo = entry.getValue();
                    break;
                }
            }
            
            if (targetCommand == null) {
                player.sendSystemMessage(Component.literal(
                    "§c未找到别名为 §e" + alias + " §c的UI注册"));
                return 0;
            }
            
            // 从映射中移除
            commandUiMap.remove(targetCommand);
            
            // 保存更新后的注册信息
            saveRegistrations();
            
            player.sendSystemMessage(Component.literal("§a✓ UI注销成功！"));
            player.sendSystemMessage(Component.literal("§7  别名: §f" + alias));
            player.sendSystemMessage(Component.literal("§7  命令: §f/" + targetCommand));
            player.sendSystemMessage(Component.literal("§7  文件: §f" + targetInfo.zipFileName));
            
            LOGGER.info("手动注销UI: command={}, alias={}", targetCommand, alias);
            
            return 1;
            
        } catch (Exception e) {
            LOGGER.error("注销UI失败: {}", e.getMessage(), e);
            player.sendSystemMessage(Component.literal(
                "§c注销失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 手动注册UI（通过/guireg命令）
     */
    private int manualRegisterUi(ServerPlayer player, String zipFileName, String alias, String description) {
        try {
            // 验证ZIP文件是否存在
            String zipPath = RESOURCE_PACK_DIR + zipFileName;
            java.io.File zipFile = new java.io.File(zipPath);
            
            if (!zipFile.exists()) {
                player.sendSystemMessage(Component.literal(
                    "§c资源包文件不存在: §e" + zipFileName));
                player.sendSystemMessage(Component.literal(
                    "§7请确保文件位于: §e" + new java.io.File(zipPath).getAbsolutePath()));
                return 0;
            }
            
            // 生成默认命令名称（使用别名）
            String command = "gui:" + alias;
            
            // 创建UI信息
            UiResourceInfo uiInfo = new UiResourceInfo(alias, command, zipFileName, 
                description.isEmpty() ? alias : description);
            
            // 检查是否已存在
            if (commandUiMap.containsKey(command)) {
                player.sendSystemMessage(Component.literal(
                    "§e警告: 命令 §f/" + command + " §e已存在，将被覆盖"));
            }
            
            // 注册UI
            commandUiMap.put(command, uiInfo);
            
            // 保存注册信息
            saveRegistrations();
            
            player.sendSystemMessage(Component.literal(
                "§a✓ UI注册成功！"));
            player.sendSystemMessage(Component.literal(
                "§7  命令: §f/" + command));
            player.sendSystemMessage(Component.literal(
                "§7  别名: §f" + alias));
            player.sendSystemMessage(Component.literal(
                "§7  文件: §f" + zipFileName));
            player.sendSystemMessage(Component.literal(
                "§7  描述: §f" + uiInfo.description));
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal(
                "§7测试命令: §e/mgui_open @s " + command));
            
            LOGGER.info("手动注册UI: command={}, alias={}, zip={}", command, alias, zipFileName);
            
            // 广播注册信息（发送给请求的玩家）
            broadcastUiRegistration(uiInfo, player);
            
            return 1;
            
        } catch (Exception e) {
            LOGGER.error("手动注册UI失败: {}", e.getMessage(), e);
            player.sendSystemMessage(Component.literal(
                "§c注册失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 强制关闭指定玩家的所有UI
     */
    public static int closeUiForPlayer(ServerPlayer player) {
        try {
            JsonObject message = new JsonObject();
            message.addProperty("type", "close_ui");
            
            MGuiNetworkAdapter.sendToClient(player, message);
            
            LOGGER.info("强制关闭玩家 {} 的所有UI", player.getName().getString());
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("关闭UI失败: {}", e.getMessage(), e);
            player.sendSystemMessage(Component.literal("§c关闭UI失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 直接为指定玩家打开UI（使用参数构建）
     * 分辨率等级：1=全屏幕 2=自定义大小 3=正常预设 49=保持安静
     */
    public static int openUiDirectForPlayer(ServerPlayer player, String url, String resolution, String width, String height, String x, String y) {
        try {
            JsonObject message = new JsonObject();
            message.addProperty("type", "open_ui_direct");
            message.addProperty("url", url);
            message.addProperty("resolution", resolution);
            message.addProperty("width", width);
            message.addProperty("height", height);
            message.addProperty("x", x);
            message.addProperty("y", y);
            
            MGuiNetworkAdapter.sendToClient(player, message);
            
            LOGGER.info("为玩家 {} 直接打开UI: url={}, resolution={}, width={}, height={}, x={}, y={}", 
                player.getName().getString(), url, resolution, width, height, x, y);
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("直接打开UI失败: {}", e.getMessage(), e);
            player.sendSystemMessage(Component.literal("§c打开UI失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 列出所有注册的UI
     */
    private void listRegisteredUis(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§a=== 已注册的UI列表 ==="));
        for (Map.Entry<String, UiResourceInfo> entry : commandUiMap.entrySet()) {
            UiResourceInfo info = entry.getValue();
            player.sendSystemMessage(Component.literal(
                String.format("§e命令: §f/%s §7- §f%s §7(别名: %s)", 
                    info.command, info.description, info.alias)
            ));
        }
        player.sendSystemMessage(Component.literal("§a======================="));
    }
    
    /**
     * UI资源信息record类
     */
    public record UiResourceInfo(
        String alias,        // 别名（简称）
        String command,      // 触发命令
        String zipFileName,  // ZIP文件名（URL模式时为空）
        String description,  // 描述
        String url,          // URL地址（URL模式时使用）
        String resolution,   // 分辨率设置：1=全屏 2=自定义 3=默认 49=保持安静
        String width,        // 窗口宽度（分辨率=2时使用）
        String height        // 窗口高度（分辨率=2时使用）
    ) {
        // 用于ZIP模式的构造函数（使用默认分辨率参数）
        public UiResourceInfo(String alias, String command, String zipFileName, String description) {
            this(alias, command, zipFileName, description, "", "3", "0", "0");
        }
    }
    
    /**
     * 通过URL注册UI资源
     */
    private int registerUiByUrl(ServerPlayer player, String alias, String url, 
                                String resolution, String width, String height, String description) {
        try {
            // 生成命令名称
            String command = "gui:" + alias;
            
            // 创建UI信息（URL模式）
            UiResourceInfo uiInfo = new UiResourceInfo(alias, command, "", 
                description.isEmpty() ? alias : description, url, 
                resolution, width, height);
            
            // 检查是否已存在
            if (commandUiMap.containsKey(command)) {
                player.sendSystemMessage(Component.literal(
                    "§e警告: 命令 §f/" + command + " §e已存在，将被覆盖"));
            }
            
            // 注册UI
            commandUiMap.put(command, uiInfo);
            
            // 保存注册信息
            saveRegistrations();
            
            player.sendSystemMessage(Component.literal("§a✓ URL UI注册成功！"));
            player.sendSystemMessage(Component.literal("§7  命令: §f/" + command));
            player.sendSystemMessage(Component.literal("§7  别名: §f" + alias));
            player.sendSystemMessage(Component.literal("§7  URL: §f" + url));
            player.sendSystemMessage(Component.literal("§7  分辨率: §f" + resolution));
            player.sendSystemMessage(Component.literal("§7  尺寸: §f" + width + "x" + height));
            player.sendSystemMessage(Component.literal("§7  描述: §f" + uiInfo.description()));
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§7测试命令: §e/mgui_open @s " + command));
            
            LOGGER.info("通过URL注册UI: command={}, alias={}, url={}", command, alias, url);
            
            // 广播注册信息（发送给请求的玩家）
            broadcastUiRegistration(uiInfo, player);
            
            return 1;
            
        } catch (Exception e) {
            LOGGER.error("通过URL注册UI失败: {}", e.getMessage(), e);
            player.sendSystemMessage(Component.literal("§c注册失败: " + e.getMessage()));
            return 0;
        }
    }
}