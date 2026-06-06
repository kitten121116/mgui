package com.example.wgewge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * MGUI 网络数据包基类
 */
public abstract class MGuiPacket implements CustomPacketPayload {
    
    public static final ResourceLocation UI_REGISTER_ID = ResourceLocation.fromNamespaceAndPath("mgui", "ui_register");
    public static final ResourceLocation S2C_ID = ResourceLocation.fromNamespaceAndPath("mgui", "s2c");
    public static final ResourceLocation C2S_ID = ResourceLocation.fromNamespaceAndPath("mgui", "c2s");
    public static final ResourceLocation CLIENT_REQUEST_ID = ResourceLocation.fromNamespaceAndPath("mgui", "client_request");
    public static final ResourceLocation RAW_ID = ResourceLocation.fromNamespaceAndPath("mgui", "main");
    
    protected final String data;
    
    public MGuiPacket(String data) {
        this.data = data;
    }
    
    public String getData() {
        return data;
    }
    
    /**
     * UI注册数据包
     */
    public static class UiRegisterPacket extends MGuiPacket {
        public static final Type<UiRegisterPacket> TYPE = new Type<>(UI_REGISTER_ID);
        
        public static final StreamCodec<FriendlyByteBuf, UiRegisterPacket> CODEC = 
            StreamCodec.of(
                (buf, packet) -> buf.writeUtf(packet.data),
                buf -> new UiRegisterPacket(buf.readUtf(32767))
            );
        
        public UiRegisterPacket(String data) {
            super(data);
        }
        
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
    
    /**
     * 服务器到客户端数据包
     */
    public static class S2CPacket extends MGuiPacket {
        public static final Type<S2CPacket> TYPE = new Type<>(S2C_ID);
        
        public static final StreamCodec<FriendlyByteBuf, S2CPacket> CODEC = 
            StreamCodec.of(
                (buf, packet) -> buf.writeUtf(packet.data),
                buf -> new S2CPacket(buf.readUtf(32767))
            );
        
        public S2CPacket(String data) {
            super(data);
        }
        
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
    
    /**
     * 客户端到服务器数据包
     */
    public static class C2SPacket extends MGuiPacket {
        public static final Type<C2SPacket> TYPE = new Type<>(C2S_ID);
        
        public static final StreamCodec<FriendlyByteBuf, C2SPacket> CODEC = 
            StreamCodec.of(
                (buf, packet) -> buf.writeUtf(packet.data),
                buf -> new C2SPacket(buf.readUtf(32767))
            );
        
        public C2SPacket(String data) {
            super(data);
        }
        
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
    
    /**
     * 客户端请求数据包
     */
    public static class ClientRequestPacket extends MGuiPacket {
        public static final Type<ClientRequestPacket> TYPE = new Type<>(CLIENT_REQUEST_ID);
        
        public static final StreamCodec<FriendlyByteBuf, ClientRequestPacket> CODEC = 
            StreamCodec.of(
                (buf, packet) -> buf.writeUtf(packet.data),
                buf -> new ClientRequestPacket(buf.readUtf(32767))
            );
        
        public ClientRequestPacket(String data) {
            super(data);
        }
        
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
    
    /**
     * 原始数据包（使用mgui:main通道，兼容Paper插件）
     */
    public static class RawPacket extends MGuiPacket {
        public static final Type<RawPacket> TYPE = new Type<>(RAW_ID);
        
        public static final StreamCodec<FriendlyByteBuf, RawPacket> CODEC = 
            StreamCodec.of(
                (buf, packet) -> buf.writeUtf(packet.data),
                buf -> new RawPacket(buf.readUtf(32767))
            );
        
        public RawPacket(String data) {
            super(data);
        }
        
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}