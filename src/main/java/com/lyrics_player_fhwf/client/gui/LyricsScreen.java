package com.lyrics_player_fhwf.client.gui;

import com.lyrics_player_fhwf.common.LyricsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.io.File;
import java.util.List;

public class LyricsScreen extends Screen {
    private final LyricsManager manager = LyricsManager.getInstance();
    private EditBox filePathField;
    private int selectedIndex = -1;
    private Button loadButton;
    private Button playButton;
    private Button stopButton;
    private Button importButton;
    private List<Button> fileButtons = new java.util.ArrayList<>();

    public LyricsScreen() {
        super(Component.literal("歌词播放器"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 文件路径输入框 - 下移避免遮挡
        this.filePathField = new EditBox(
            this.font,
            centerX - 150,
            centerY - 60,
            250,
            20,
            Component.literal("请输入LRC文件路径")
        );
        this.filePathField.setMaxLength(256);
        this.addWidget(this.filePathField);

        // 导入按钮
        this.importButton = Button.builder(
            Component.literal("导入"),
            button -> importLrcFile()
        ).bounds(centerX + 110, centerY - 60, 40, 20).build();
        this.addRenderableWidget(this.importButton);

        // 加载按钮
        this.loadButton = Button.builder(
            Component.literal("加载"),
            button -> loadSelected()
        ).bounds(centerX - 110, centerY + 30, 60, 20).build();
        this.loadButton.active = false;
        this.addRenderableWidget(this.loadButton);

        // 播放按钮
        this.playButton = Button.builder(
            Component.literal("播放"),
            button -> togglePlay()
        ).bounds(centerX - 40, centerY + 30, 60, 20).build();
        this.playButton.active = false;
        this.addRenderableWidget(this.playButton);

        // 停止按钮
        this.stopButton = Button.builder(
            Component.literal("停止"),
            button -> stopPlayback()
        ).bounds(centerX + 30, centerY + 30, 60, 20).build();
        this.stopButton.active = false;
        this.addRenderableWidget(this.stopButton);

        // 刷新列表
        updateList();
    }

    private void updateList() {
        // 清除旧列表按钮
        fileButtons.forEach(this::removeWidget);
        fileButtons.clear();

        List<LyricsManager.LyricsFile> files = manager.getLyricsFiles();
        int startY = this.height / 2 - 40;

        for (int i = 0; i < files.size(); i++) {
            final int index = i;
            LyricsManager.LyricsFile file = files.get(i);
            String title = (i == selectedIndex ? ChatFormatting.GREEN.toString() : "") + file.getTitle();

            Button fileButton = Button.builder(
                Component.literal(title),
                button -> selectFile(index)
            ).bounds(this.width / 2 - 150, startY + i * 25, 300, 20).build();

            this.addRenderableWidget(fileButton);
            fileButtons.add(fileButton);
        }
    }

    private void selectFile(int index) {
        this.selectedIndex = index;
        this.loadButton.active = true;
        this.playButton.active = false;
        this.stopButton.active = false;
        updateList();
    }

    private void loadSelected() {
        if (selectedIndex >= 0 && selectedIndex < manager.getLyricsFiles().size()) {
            manager.setCurrentLyrics(selectedIndex);
            manager.loadCurrentLyrics();
            this.playButton.active = true;
            this.stopButton.active = false;
            this.loadButton.active = false;
            this.playButton.setMessage(Component.literal("播放"));
        }
    }

    private void importLrcFile() {
        String path = filePathField.getValue();
        if (path.isEmpty()) {
            return;
        }

        // 支持带引号的路径
        path = path.replace("\"", "").trim();

        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            filePathField.setValue("文件不存在！");
            return;
        }

        if (!file.getName().toLowerCase().endsWith(".lrc")) {
            filePathField.setValue("请选择LRC文件！");
            return;
        }

        if (manager.importLrcFile(file)) {
            filePathField.setValue("导入成功！");
            selectedIndex = -1;
            loadButton.active = false;
            playButton.active = false;
            stopButton.active = false;
            updateList();
        } else {
            filePathField.setValue("导入失败！");
        }
    }

    private void togglePlay() {
        if (!manager.isPlaying()) {
            // 开始播放
            manager.play(Minecraft.getInstance());
            playButton.setMessage(Component.literal("暂停"));
            stopButton.active = true;
        } else {
            // 暂停/恢复
            manager.togglePlay();
            if (manager.isPaused()) {
                playButton.setMessage(Component.literal("继续"));
            } else if (manager.isPlaying()) {
                playButton.setMessage(Component.literal("暂停"));
            }
        }
    }

    private void stopPlayback() {
        manager.stopPlayback();
        playButton.setMessage(Component.literal("播放"));
        stopButton.active = false;
        // 发送停止提示
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(
                Component.literal("§c[歌词] §f已停止播放")
            );
        }
    }

    @Override
    public void tick() {
        super.tick();
        // 更新播放按钮状态
        if (playButton.active) {
            if (manager.isPlaying() && !manager.isPaused()) {
                playButton.setMessage(Component.literal("暂停"));
                stopButton.active = true;
            } else if (manager.isPaused()) {
                playButton.setMessage(Component.literal("继续"));
                stopButton.active = true;
            } else {
                playButton.setMessage(Component.literal("播放"));
                stopButton.active = false;
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 标题
        guiGraphics.drawString(
            this.font,
            Component.literal("歌词播放器").withStyle(ChatFormatting.BOLD),
            centerX - 50,
            20,
            0xFFFFFF,
            true
        );

        // 文件路径标签 - 下移
        guiGraphics.drawString(
            this.font,
            "LRC文件路径:",
            centerX - 150,
            centerY - 75,
            0xAAAAAA,
            false
        );

        // 文件列表标签 - 下移
        guiGraphics.drawString(
            this.font,
            "已导入的歌词:",
            centerX - 150,
            centerY - 50,
            0xAAAAAA,
            false
        );

        // 状态信息
        if (selectedIndex >= 0 && selectedIndex < manager.getLyricsFiles().size()) {
            LyricsManager.LyricsFile file = manager.getLyricsFiles().get(selectedIndex);
            String status = manager.isPlaying() ? "§a播放中" : "§7已停止";
            if (manager.isPaused()) {
                status = "§e已暂停";
            }
            guiGraphics.drawString(
                this.font,
                "已选择: " + file.getTitle() + " (" + file.getLines().size() + "句) " + status,
                centerX - 150,
                centerY + 60,
                0x88FF88,
                false
            );
        }

        // 文件路径输入框
        this.filePathField.render(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}