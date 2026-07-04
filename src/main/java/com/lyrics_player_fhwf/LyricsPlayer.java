package com.lyrics_player_fhwf;

import com.lyrics_player_fhwf.common.LyricsManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LyricsPlayer implements ModInitializer {
    public static final String MOD_ID = "lyrics-player";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    public static final ResourceLocation LYRICS_PACKET_ID = new ResourceLocation(MOD_ID, "lyrics");

    @Override
    public void onInitialize() {
        LOGGER.info("LyricsPlayer initialized!");

        // 服务器端接收歌词数据包
        ServerPlayNetworking.registerGlobalReceiver(LYRICS_PACKET_ID,
            (server, player, handler, buf, responseSender) -> {
                String message = buf.readUtf(32767);
                server.execute(() -> {
                    // 在服务器端发送聊天消息
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
                });
            });

        // 服务器关闭时清理
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LyricsManager.getInstance().clear();
        });
    }
}