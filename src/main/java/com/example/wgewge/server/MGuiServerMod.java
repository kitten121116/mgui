package com.example.wgewge.server;

import com.example.wgewge.MGuiConstants;
import com.example.wgewge.network.MGuiNetworkAdapter;
import com.example.wgewge.network.MGuiPacket;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
     * 注册玩家连接事件
     */
    private void registerPlayerEvents() {
        // 玩家加入事件 - 初始化Mod状态
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            playerModStatus.put(player.getUUID(), false);
            LOGGER.debug("玩家 {} ({}) 加入服务器，初始Mod状态: 未安装", player.getName().getString(), player.getUUID());
        });
        
        // 玩家离开事件 - 清理状态
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            playerModStatus.remove(player.getUUID());
            LOGGER.debug("玩家 {} ({}) 离开服务器，已清理状态", player.getName().getString(), player.getUUID());
        });
    }
    
    /**
     * 确保资源目录存在
     */
    private void ensureResourceDir() {
        try {
            java.io.File dir = new java.io.File(RESOURCE_PACK_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
                LOGGER.info("创建资源目录: {}", dir.getAbsolutePath());
            }
        } catch (Exception e) {
            LOGGER.error("创建资源目录失败: {}", e.getMessage());
        }
    }
    
    /**
     * 注册命令
     */
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // 注册 /mgui_open 命令 - 为指定玩家打开UI
            dispatcher.register(
                Commands.literal("mgui_open")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("uiCommand", StringArgumentType.greedyString())
                            .executes(context -> {
                                ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
                                String uiCommand = StringArgumentType.getString(context, "uiCommand");
                                return openUiForPlayer(targetPlayer, uiCommand);
                            })
                        )
                    )
            );
            
            // 注册 /mgui_open2 命令 - 强制打开玩家的UI，直接构建参数
            // 用法: /mgui_open2 <player> <resolution> <width> <height> <url>
            // 分辨率等级: 1=全屏 2=自定义大小 3=正常预设 49=保持安静
            dispatcher.register(
                Commands.literal("mgui_open2")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("resolution", StringArgumentType.word())
                            .then(Commands.argument("width", StringArgumentType.word())
                                .then(Commands.argument("height", StringArgumentType.word())
                                    .then(Commands.argument("url", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
                                            String resolution = StringArgumentType.getString(context, "resolution");
                                            String width = StringArgumentType.getString(context, "width");
                                            String height = StringArgumentType.getString(context, "height");
                                            String url = StringArgumentType.getString(context, "url");
                                            return openUiDirectForPlayer(targetPlayer, url, resolution, width, height);
                                        })
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
                Commands.literal("mgui_list")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> {
                        listRegisteredUis(context.getSource().getPlayerOrException());
                        return 1;
                    })
            );
            
            // 注册 /guireg 命令 - 手动注册UI（用于调试）
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
            // 用法: /guireg_url <alias> <resolution> <width> <height> <url>
            // 使用greedyString捕获所有参数，手动解析
            // 分辨率等级: 1=全屏 2=自定义大小 3=正常预设 49=保持安静
            dispatcher.register(
                Commands.literal("guireg_url")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(context -> {
                            String allArgs = StringArgumentType.getString(context, "args");
                            LOGGER.info("原始参数: {}", allArgs);
                            
                            // 手动解析参数
                            String[] parts = allArgs.split(" ", 5); // 分割成最多5部分
                            if (parts.length < 5) {
                                context.getSource().sendFailure(Component.literal("§c参数不足！用法: /guireg_url <别名> <分辨率> <宽度> <高度> <URL>"));
                                return 0;
                            }
                            
                            String alias = parts[0];
                            String resolution = parts[1];
                            String width = parts[2];
                            String height = parts[3];
                            String url = parts[4];
                            
                            // 移除URL前后的引号（如果有的话）
                            if (url.startsWith("\"") && url.endsWith("\"")) {
                                url = url.substring(1, url.length() - 1);
                            }
                            
                            LOGGER.info("解析参数: alias={}, resolution={}, width={}, height={}, url={}", alias, resolution, width, height, url);
                            
                            // 验证参数
                            if (url.contains(" ")) {
                                context.getSource().sendFailure(Component.literal("§cURL中不能包含空格！请使用引号包围URL"));
                                return 0;
                            }
                            
                            return registerUiByUrl(context.getSource().getPlayerOrException(), alias, url, resolution, width, height, "");
                        })
                    )
            );
        });
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
     * 为指定玩家打开UI（支持ZIP模式和URL模式）
     */
    public static int openUiForPlayer(ServerPlayer player, String command) {
        // 检查玩家是否安装了客户端Mod
        if (!hasClientMod(player)) {
            player.sendSystemMessage(Component.literal("§c[MGUI] 您需要安装MGUI客户端Mod才能使用此功能！"));
            player.sendSystemMessage(Component.literal("§c[MGUI] 下载地址: https://example.com/mgui-mod.jar"));
            player.sendSystemMessage(Component.literal("§c[MGUI] 安装后重启游戏即可使用UI功能"));
            return 0;
        }
        
        UiResourceInfo uiInfo = commandUiMap.get(command);
        if (uiInfo == null) {
            player.sendSystemMessage(Component.literal("§c未找到UI资源: " + command));
            return 0;
        }
        
        // 判断是URL模式还是ZIP模式
        if (!uiInfo.url().isEmpty()) {
            // URL模式 - 直接打开
            return openUiDirectForPlayer(player, uiInfo.url(), uiInfo.resolution(), uiInfo.width(), uiInfo.height());
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
            
            // 使用私有协议发送打开UI指令（包含元数据和MD5）
            MGuiNetworkAdapter.sendOpenUi(player, uiInfo.alias(), uiInfo.command(), uiInfo.description(), md5);
            
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
     * 直接为指定玩家打开UI（使用参数构建）
     * 参数格式: gui.exe <url> <resolution> <width> <height>
     * 分辨率等级：1=全屏幕 2=自定义大小 3=正常预设 49=保持安静
     */
    public static int openUiDirectForPlayer(ServerPlayer player, String url, String resolution, String width, String height) {
        try {
            JsonObject message = new JsonObject();
            // 使用私有协议发送直接打开UI指令
            MGuiNetworkAdapter.sendOpenUiDirect(player, url, resolution, width, height);
            
            LOGGER.info("为玩家 {} 直接打开UI: url={}, resolution={}, width={}, height={}", 
                player.getName().getString(), url, resolution, width, height);
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("直接打开UI失败: {}", e.getMessage(), e);
            player.sendSystemMessage(Component.literal("§c打开UI失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 关闭指定玩家的UI
     */
    public static int closeUiForPlayer(ServerPlayer player) {
        try {
            // 使用私有协议发送关闭UI指令
            MGuiNetworkAdapter.sendCloseUi(player);
            
            LOGGER.info("为玩家 {} 关闭UI", player.getName().getString());
            player.sendSystemMessage(Component.literal("§a已关闭所有UI"));
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("关闭UI失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 手动注册UI资源（用于调试）
     */
    private int manualRegisterUi(ServerPlayer player, String zipFileName, String alias, String description) {
        try {
            String command = "gui:" + alias;
            
            // 检查ZIP文件是否存在
            java.io.File zipFile = new java.io.File(RESOURCE_PACK_DIR + zipFileName);
            if (!zipFile.exists()) {
                player.sendSystemMessage(Component.literal("§cZIP文件不存在: " + zipFileName));
                return 0;
            }
            
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
            
            player.sendSystemMessage(Component.literal("§a✓ UI注册成功！"));
            player.sendSystemMessage(Component.literal("§7  命令: §f/" + command));
            player.sendSystemMessage(Component.literal("§7  别名: §f" + alias));
            player.sendSystemMessage(Component.literal("§7  ZIP文件: §f" + zipFileName));
            player.sendSystemMessage(Component.literal("§7  描述: §f" + uiInfo.description()));
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§7测试命令: §e/mgui_open @s " + command));
            
            LOGGER.info("手动注册UI: command={}, alias={}, zip={}", command, alias, zipFileName);
            
            return 1;
            
        } catch (Exception e) {
            LOGGER.error("注册UI失败: {}", e.getMessage(), e);
            player.sendSystemMessage(Component.literal("§c注册失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 通过URL注册UI资源
     */
    private int registerUiByUrl(ServerPlayer player, String alias, String url, 
                                String resolution, String width, String height, String description) {
        LOGGER.info("registerUiByUrl 被调用: alias={}, url={}, resolution={}, width={}, height={}", alias, url, resolution, width, height);
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
            player.sendSystemMessage(Component.literal("§7  分辨率: §f" + resolution));
            player.sendSystemMessage(Component.literal("§7  尺寸: §f" + width + "x" + height));
            player.sendSystemMessage(Component.literal("§7  描述: §f" + uiInfo.description()));
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§7测试命令: §e/mgui_open @s " + command));
            
            LOGGER.info("通过URL注册UI: command={}, alias={}, url={}", command, alias, url);
            
            return 1;
            
        } catch (Exception e) {
            LOGGER.error("注册URL UI失败: {}", e.getMessage(), e);
            player.sendSystemMessage(Component.literal("§c注册失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 取消注册UI资源
     */
    private int unregisterUi(ServerPlayer player, String alias) {
        String command = "gui:" + alias;
        
        if (!commandUiMap.containsKey(command)) {
            player.sendSystemMessage(Component.literal("§c未找到UI资源: " + alias));
            return 0;
        }
        
        commandUiMap.remove(command);
        saveRegistrations();
        
        player.sendSystemMessage(Component.literal("§a✓ UI已删除: " + alias));
        LOGGER.info("删除UI注册: {}", command);
        
        return 1;
    }
    
    /**
     * 列出所有已注册的UI
     */
    private void listRegisteredUis(ServerPlayer player) {
        if (commandUiMap.isEmpty()) {
            player.sendSystemMessage(Component.literal("§e暂无已注册的UI"));
            return;
        }
        
        player.sendSystemMessage(Component.literal("§a=== 已注册的UI列表 ==="));
        int index = 1;
        for (Map.Entry<String, UiResourceInfo> entry : commandUiMap.entrySet()) {
            UiResourceInfo info = entry.getValue();
            player.sendSystemMessage(Component.literal(
                "§7" + index + ". §f/" + info.command() + 
                " §8- §e" + info.alias() + 
                " §8(§7" + info.description() + "§8)"
            ));
            index++;
        }
        player.sendSystemMessage(Component.literal("§a======================"));
        player.sendSystemMessage(Component.literal("§7共 " + commandUiMap.size() + " 个UI"));
    }
    
    /**
     * 处理UI注册请求（来自业务mod）
     */
    private void handleUiRegistration(ServerPlayer player, String jsonData) {
        try {
            JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
            
            String command = json.get("command").getAsString();
            String alias = json.has("alias") ? json.get("alias").getAsString() : command;
            String zipFileName = json.get("zipFileName").getAsString();
            String description = json.has("description") ? json.get("description").getAsString() : "";
            
            // 创建UI信息
            UiResourceInfo uiInfo = new UiResourceInfo(alias, command, zipFileName, description);
            
            // 检查是否已存在
            if (commandUiMap.containsKey(command)) {
                LOGGER.warn("UI命令已存在，将被覆盖: {}", command);
            }
            
            // 注册UI
            commandUiMap.put(command, uiInfo);
            
            // 保存注册信息
            saveRegistrations();
            
            LOGGER.info("收到UI注册: command={}, alias={}, zip={}", command, alias, zipFileName);
            
        } catch (Exception e) {
            LOGGER.error("处理UI注册失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 处理客户端请求
     */
    // 跟踪玩家是否安装了客户端Mod（使用UUID作为键，更可靠）
    private static final Map<java.util.UUID, Boolean> playerModStatus = new java.util.HashMap<>();
    
    private void handleClientRequest(ServerPlayer player, String jsonData) {
        try {
            JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
            String requestType = json.get("type").getAsString();
            
            switch (requestType) {
                case "handshake":
                    handleHandshake(player);
                    break;
                case "screenshot":
                    handleScreenshotRequest(player, json);
                    break;
                default:
                    LOGGER.warn("未知的客户端请求类型: {}", requestType);
            }
            
        } catch (Exception e) {
            LOGGER.error("处理客户端请求失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 处理客户端握手消息
     */
    private void handleHandshake(ServerPlayer player) {
        playerModStatus.put(player.getUUID(), true);
        LOGGER.info("玩家 {} ({}) 已安装 MGUI 客户端Mod", player.getName().getString(), player.getUUID());
    }
    
    /**
     * 检查玩家是否安装了客户端Mod
     */
    public static boolean hasClientMod(ServerPlayer player) {
        return playerModStatus.getOrDefault(player.getUUID(), false);
    }
    
    /**
     * 处理截图请求
     */
    private void handleScreenshotRequest(ServerPlayer player, JsonObject json) {
        // 截图请求通常在客户端处理，这里可以记录日志或做其他处理
        LOGGER.info("收到玩家 {} 的截图请求", player.getName().getString());
    }
    
    /**
     * 发送ZIP数据到客户端
     */
    private void sendZipDataToClient(ServerPlayer player, byte[] zipData) {
        int chunkSize = 32000;
        int totalChunks = (int) Math.ceil((double) zipData.length / chunkSize);
        
        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, zipData.length);
            byte[] chunk = java.util.Arrays.copyOfRange(zipData, start, end);
            
            JsonObject chunkMessage = new JsonObject();
            chunkMessage.addProperty("type", "zip_chunk");
            chunkMessage.addProperty("chunkIndex", i);
            // 使用私有协议发送ZIP数据块
            MGuiNetworkAdapter.sendZipChunk(player, i, totalChunks, chunk);
        }
        
        // 使用私有协议发送ZIP完成消息
        MGuiNetworkAdapter.sendZipComplete(player);
    }
    
    /**
     * 计算MD5值
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
     * 保存注册信息到文件
     */
    private void saveRegistrations() {
        try {
            JsonObject jsonObj = new JsonObject();
            
            for (Map.Entry<String, UiResourceInfo> entry : commandUiMap.entrySet()) {
                UiResourceInfo info = entry.getValue();
                JsonObject uiObj = new JsonObject();
                uiObj.addProperty("alias", info.alias());
                uiObj.addProperty("command", info.command());
                uiObj.addProperty("zipFileName", info.zipFileName());
                uiObj.addProperty("description", info.description());
                uiObj.addProperty("url", info.url());
                uiObj.addProperty("resolution", info.resolution());
                uiObj.addProperty("width", info.width());
                uiObj.addProperty("height", info.height());
                jsonObj.add(entry.getKey(), uiObj);
            }
            
            String json = new com.google.gson.Gson().toJson(jsonObj);
            java.nio.file.Files.write(
                java.nio.file.Paths.get(REGISTRATION_FILE), 
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
            
            LOGGER.debug("注册信息已保存");
            
        } catch (Exception e) {
            LOGGER.error("保存注册信息失败: {}", e.getMessage());
        }
    }
    
    /**
     * 加载注册信息
     */
    private void loadRegistrations() {
        try {
            java.nio.file.Path regPath = java.nio.file.Paths.get(REGISTRATION_FILE);
            if (java.nio.file.Files.exists(regPath)) {
                String json = new String(java.nio.file.Files.readAllBytes(regPath));
                JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
                
                for (String key : jsonObj.keySet()) {
                    JsonObject uiObj = jsonObj.get(key).getAsJsonObject();
                    
                    String alias = uiObj.get("alias").getAsString();
                    String command = uiObj.get("command").getAsString();
                    String zipFileName = uiObj.has("zipFileName") ? uiObj.get("zipFileName").getAsString() : "";
                    String description = uiObj.has("description") ? uiObj.get("description").getAsString() : "";
                    String url = uiObj.has("url") ? uiObj.get("url").getAsString() : "";
                    String resolution = uiObj.has("resolution") ? uiObj.get("resolution").getAsString() : "3";
                    String width = uiObj.has("width") ? uiObj.get("width").getAsString() : "0";
                    String height = uiObj.has("height") ? uiObj.get("height").getAsString() : "0";
                    
                    UiResourceInfo uiInfo = new UiResourceInfo(alias, command, zipFileName, description, url, resolution, width, height);
                    commandUiMap.put(command, uiInfo);
                }
                
                LOGGER.info("已加载 {} 个UI注册", commandUiMap.size());
            }
        } catch (Exception e) {
            LOGGER.warn("加载注册信息失败: {}", e.getMessage());
        }
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
}