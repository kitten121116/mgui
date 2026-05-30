package com.example.wgewge.client.mixin;

import com.example.wgewge.network.MGuiPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin 拦截 Plugin Message
 * 用于兼容自定义数据包处理
 */
@Mixin(net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket.class)
public class ClientboundCustomPayloadPacketMixin {
    
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void onHandle(CallbackInfo ci) {
        net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet = 
            (net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket)(Object)this;
        
        // 获取 CustomPacketPayload
        CustomPacketPayload payload = packet.payload();
        
        // 检查是否是 MGUI 的 S2CPacket
        if (payload instanceof MGuiPacket.S2CPacket s2cPacket) {
            try {
                // 直接获取数据
                String jsonData = s2cPacket.getData();
                
                // 在游戏主线程处理消息
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft != null) {
                    minecraft.execute(() -> {
                        // 调用客户端模组的消息处理方法
                        com.example.wgewge.client.MGuiClientMod clientMod = 
                            com.example.wgewge.client.MGuiClientMod.getInstance();
                        if (clientMod != null) {
                            clientMod.handleServerMessageFromPluginMessage(jsonData);
                        }
                    });
                }
                
                // 取消原有处理
                ci.cancel();
                
            } catch (Exception e) {
                com.example.wgewge.client.MGuiClientMod.LOGGER.error(
                    "处理 Plugin Message 失败: {}", e.getMessage(), e);
            }
        }
    }
}