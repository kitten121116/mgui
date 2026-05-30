package com.example.wgewge.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MGUI 客户端入口
 */
public class TemplateClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("mgui_client_entry");

	@Override
	public void onInitializeClient() {
		LOGGER.info("MGUI 客户端模组正在初始化...");
		
		// 委托给 MGuiClientMod 处理
		MGuiClientMod clientMod = new MGuiClientMod();
		clientMod.onInitializeClient();
		
		LOGGER.info("MGUI 客户端模组初始化完成");
	}
}
