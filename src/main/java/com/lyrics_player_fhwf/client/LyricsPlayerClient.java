package com.lyrics_player_fhwf.client;

import com.lyrics_player_fhwf.LyricsPlayer;
import com.lyrics_player_fhwf.client.gui.LyricsScreen;
import com.lyrics_player_fhwf.common.LyricsManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class LyricsPlayerClient implements ClientModInitializer {
    private static KeyMapping openGuiKey;
    private static KeyMapping playNextKey;

    @Override
    public void onInitializeClient() {
        // 注册按键：打开GUI（分号键）
        openGuiKey = new KeyMapping(
            "key.lyrics-player.open_gui",
            GLFW.GLFW_KEY_SEMICOLON,
            "category.lyrics-player"
        );
        KeyBindingHelper.registerKeyBinding(openGuiKey);

        // 注册按键：播放下一句/开始自动播放/停止（上箭头）
        playNextKey = new KeyMapping(
            "key.lyrics-player.next_lyric",
            GLFW.GLFW_KEY_UP,
            "category.lyrics-player"
        );
        KeyBindingHelper.registerKeyBinding(playNextKey);

        // 客户端Tick事件处理
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiKey.consumeClick()) {
                client.setScreen(new LyricsScreen());
            }

            if (playNextKey.consumeClick()) {
                LyricsManager.getInstance().playNextLyric(client);
            }
        });
    }
}