package com.danjvan.mgui.mixin;

import com.danjvan.mgui.network.MGuiPluginMessageHandler;
import com.danjvan.mgui.util.MGuiLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截客户端接收的自定义 Payload 包
 * 
 * 用于兼容 Bukkit 服务器的传统 Plugin Message
 */
@Mixin(ClientboundCustomPayloadPacket.class)
public class ClientboundCustomPayloadPacketMixin {
    
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void onHandle(CallbackInfo ci) {
        try {
            // 获取通道 ID 和数据
            ClientboundCustomPayloadPacket packet = (ClientboundCustomPayloadPacket) (Object) this;
            
            // 通过反射获取 payload 数据
            ResourceLocation channel = getChannel(packet);
            FriendlyByteBuf data = getData(packet);
            
            // 检查是否是 MGUI 通道
            if (channel != null && channel.equals(MGuiPluginMessageHandler.CHANNEL_ID)) {
                String message = data.readUtf(32767);
                MGuiLogger.debug("通过 Mixin 拦截到 MGUI 消息: " + message);
                
                // 处理消息
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> MGuiPluginMessageHandler.handleMessage(message));
                
                // 取消默认处理（避免解码错误）
                ci.cancel();
            }
        } catch (Exception e) {
            MGuiLogger.error("Mixin 拦截失败: " + e.getMessage());
            // 不取消，让默认处理器继续（可能会报错但不影响游戏）
        }
    }
    
    /**
     * 通过反射获取通道 ID
     */
    private static ResourceLocation getChannel(ClientboundCustomPayloadPacket packet) {
        try {
            java.lang.reflect.Field channelField = packet.getClass().getDeclaredField("id");
            channelField.setAccessible(true);
            return (ResourceLocation) channelField.get(packet);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 通过反射获取数据缓冲区
     */
    private static FriendlyByteBuf getData(ClientboundCustomPayloadPacket packet) {
        try {
            java.lang.reflect.Field dataField = packet.getClass().getDeclaredField("data");
            dataField.setAccessible(true);
            return (FriendlyByteBuf) dataField.get(packet);
        } catch (Exception e) {
            return null;
        }
    }
}
