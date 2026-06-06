package com.example.wgewge.client.mixin;

import com.example.wgewge.client.MGuiClientMod;
import com.example.wgewge.client.network.MGuiClientNetworkHandler;
import com.example.wgewge.network.MGuiPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.StandardCharsets;

/**
 * Mixin 拦截 Plugin Message
 * 用于兼容自定义数据包处理
 * 
 * 支持两种通信方式：
 * 1. Fabric CustomPacketPayload（纯mod服务器）
 * 2. Plugin Message Channel（Paper插件服务器）
 */
@Mixin(net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket.class)
public class ClientboundCustomPayloadPacketMixin {
    
    /**
     * MGUI 插件消息通道名称
     */
    private static final ResourceLocation MGUI_CHANNEL = ResourceLocation.fromNamespaceAndPath("mgui", "main");
    
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void onHandle(CallbackInfo ci) {
        net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet = 
            (net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket)(Object)this;
        
        try {
            // 获取通道ID
            ResourceLocation channelId = getChannelId(packet);
            
            MGuiClientMod.LOGGER.debug("收到插件消息包, 通道ID: {}", channelId);
            
            // ============ 处理 Fabric CustomPacketPayload ============
            CustomPacketPayload payload = packet.payload();
            MGuiClientMod.LOGGER.debug("Payload类型: {}", payload.getClass().getName());
            
            if (payload instanceof MGuiPacket.S2CPacket s2cPacket) {
                String jsonData = s2cPacket.getData();
                MGuiClientMod.LOGGER.info("收到Fabric CustomPacketPayload消息: {}", jsonData.length() > 100 ? jsonData.substring(0, 100) + "..." : jsonData);
                processMessage(jsonData);
                ci.cancel();
                return;
            }
            
            // ============ 处理传统 Plugin Message（Paper插件） ============
            if (channelId != null && MGUI_CHANNEL.equals(channelId)) {
                MGuiClientMod.LOGGER.info("收到Plugin Message消息, 通道: {}", channelId);
                FriendlyByteBuf buf = getPacketData(packet);
                if (buf != null) {
                    MGuiClientNetworkHandler.handlePluginMessage(buf);
                    ci.cancel();
                    return;
                } else {
                    MGuiClientMod.LOGGER.warn("无法获取数据包数据");
                }
            } else if (channelId != null && channelId.getNamespace().equals("mgui")) {
                MGuiClientMod.LOGGER.debug("收到MGUI相关通道消息, 但不是主通道: {}", channelId);
            }
            
        } catch (Exception e) {
            MGuiClientMod.LOGGER.error("处理插件消息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 通过反射获取通道ID
     */
    private ResourceLocation getChannelId(net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet) {
        try {
            java.lang.reflect.Field idField = packet.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            return (ResourceLocation) idField.get(packet);
        } catch (Exception e) {
            // 尝试其他字段名
            try {
                java.lang.reflect.Field typeField = packet.getClass().getDeclaredField("type");
                typeField.setAccessible(true);
                Object typeObj = typeField.get(packet);
                if (typeObj instanceof ResourceLocation) {
                    return (ResourceLocation) typeObj;
                }
            } catch (Exception ex) {
                // 忽略
            }
            return null;
        }
    }
    
    /**
     * 通过反射获取数据包数据
     */
    private FriendlyByteBuf getPacketData(net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet) {
        try {
            java.lang.reflect.Field dataField = packet.getClass().getDeclaredField("data");
            dataField.setAccessible(true);
            return (FriendlyByteBuf) dataField.get(packet);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 处理消息
     */
    private void processMessage(String jsonData) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> {
                MGuiClientMod clientMod = MGuiClientMod.getInstance();
                if (clientMod != null) {
                    clientMod.handleServerMessageFromProtocol(jsonData);
                }
            });
        }
    }
}