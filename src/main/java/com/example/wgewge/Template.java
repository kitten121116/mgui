package com.example.wgewge;

import com.example.wgewge.network.MGuiPacket;
import com.example.wgewge.server.MGuiServerMod;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MGUI 主模组入口
 * 这是一个同时包含服务端和客户端功能的Fabric模组
 */
public class Template implements ModInitializer {
	public static final String MOD_ID = "mgui";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("MGUI 模组正在初始化...");
		
		// 注册数据包类型（必须在最前面，且只注册一次）
		registerPayloadTypes();
		
		// 初始化服务器端（如果在服务器环境）
		try {
			MGuiServerMod serverMod = new MGuiServerMod();
			serverMod.onInitializeServer();
			LOGGER.info("MGUI 服务器端初始化成功");
		} catch (Exception e) {
			LOGGER.warn("MGUI 服务器端初始化失败（可能在客户端环境）: {}", e.getMessage());
		}
		
		LOGGER.info("MGUI 模组初始化完成");
	}
	
	/**
	 * 注册数据包类型（全局只注册一次）
	 */
	private void registerPayloadTypes() {
		// 注册服务器到客户端的数据包类型
		PayloadTypeRegistry.playS2C().register(MGuiPacket.S2CPacket.TYPE, MGuiPacket.S2CPacket.CODEC);
		
		// 注册客户端到服务器的数据包类型
		PayloadTypeRegistry.playC2S().register(MGuiPacket.C2SPacket.TYPE, MGuiPacket.C2SPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(MGuiPacket.ClientRequestPacket.TYPE, MGuiPacket.ClientRequestPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(MGuiPacket.UiRegisterPacket.TYPE, MGuiPacket.UiRegisterPacket.CODEC);
		
		LOGGER.info("数据包类型注册完成");
	}
}