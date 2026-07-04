package com.lyrics_player_fhwf.common;

import com.lyrics_player_fhwf.LyricsPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricsManager {
    private static LyricsManager instance;
    private final List<LyricsFile> lyricsFiles = new ArrayList<>();
    private LyricsFile currentLyrics;
    private int currentIndex = 0;
    private boolean isPlaying = false;
    private Timer timer;
    private long startTime = 0;
    private boolean isPaused = false;
    private long pauseTime = 0;

    // LRC时间戳正则表达式
    private static final Pattern TIME_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2}\\.\\d{2})\\]");
    private static final Pattern TIME_PATTERN2 = Pattern.compile("\\[(\\d{2}):(\\d{2}):(\\d{2})\\]");

    private LyricsManager() {}

    public static LyricsManager getInstance() {
        if (instance == null) {
            instance = new LyricsManager();
        }
        return instance;
    }

    // 导入LRC文件 - 支持带引号的路径
    public boolean importLrcFile(File file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            List<LyricLine> lines = new ArrayList<>();
            String title = file.getName().replace(".lrc", "");

            while ((line = reader.readLine()) != null) {
                // 解析时间戳和歌词
                Matcher matcher = TIME_PATTERN.matcher(line);
                if (matcher.find()) {
                    int minutes = Integer.parseInt(matcher.group(1));
                    double seconds = Double.parseDouble(matcher.group(2));
                    long timeMs = (long) (minutes * 60 * 1000 + seconds * 1000);

                    String lyric = line.substring(matcher.end()).trim();
                    if (!lyric.isEmpty()) {
                        lines.add(new LyricLine(timeMs, lyric));
                    }
                } else {
                    // 尝试另一种格式
                    Matcher matcher2 = TIME_PATTERN2.matcher(line);
                    if (matcher2.find()) {
                        int minutes = Integer.parseInt(matcher2.group(1));
                        int seconds = Integer.parseInt(matcher2.group(2));
                        int millis = Integer.parseInt(matcher2.group(3));
                        long timeMs = (long) (minutes * 60 * 1000 + seconds * 1000 + millis);

                        String lyric = line.substring(matcher2.end()).trim();
                        if (!lyric.isEmpty()) {
                            lines.add(new LyricLine(timeMs, lyric));
                        }
                    }
                }
            }

            if (!lines.isEmpty()) {
                // 按时间排序
                lines.sort(Comparator.comparingLong(LyricLine::getTime));
                lyricsFiles.add(new LyricsFile(title, lines));
                return true;
            }
        } catch (IOException e) {
            LyricsPlayer.LOGGER.error("Failed to import LRC file", e);
        }
        return false;
    }

    public List<LyricsFile> getLyricsFiles() {
        return lyricsFiles;
    }

    public void setCurrentLyrics(int index) {
        if (index >= 0 && index < lyricsFiles.size()) {
            this.currentLyrics = lyricsFiles.get(index);
            this.currentIndex = 0;
            this.isPlaying = false;
            this.isPaused = false;
            stopTimer();
        }
    }

    public void loadCurrentLyrics() {
        if (currentLyrics != null) {
            currentIndex = 0;
            isPlaying = false;
            isPaused = false;
            stopTimer();
        }
    }

    // 根据时间戳播放歌词
    public void play(Minecraft client) {
        if (currentLyrics == null || currentLyrics.getLines().isEmpty()) {
            return;
        }

        if (isPlaying && !isPaused) {
            // 如果正在播放，暂停
            pause();
            return;
        }

        if (isPaused) {
            // 恢复播放
            resume(client);
            return;
        }

        // 开始播放
        startPlayback(client);
    }

    private void startPlayback(Minecraft client) {
        if (currentIndex >= currentLyrics.getLines().size()) {
            currentIndex = 0;
        }

        isPlaying = true;
        isPaused = false;
        startTime = System.currentTimeMillis();
        
        // 计算第一个歌词的延迟
        LyricLine firstLine = currentLyrics.getLines().get(currentIndex);
        long firstDelay = firstLine.getTime();
        
        // 如果第一句有延迟，先等待
        if (firstDelay > 0) {
            scheduleNextLyric(client, firstDelay);
        } else {
            // 立即发送第一句
            sendLyric(client, firstLine.getText());
            currentIndex++;
            scheduleNextLyric(client, 0);
        }
    }

    private void scheduleNextLyric(Minecraft client, long delay) {
        stopTimer();
        timer = new Timer();
        
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (isPlaying && !isPaused && client != null) {
                    client.execute(() -> {
                        if (currentLyrics != null && currentIndex < currentLyrics.getLines().size()) {
                            LyricLine currentLine = currentLyrics.getLines().get(currentIndex);
                            
                            // 计算下一句的时间差
                            long currentTime = System.currentTimeMillis() - startTime;
                            long nextTime = currentLine.getTime();
                            long timeDiff = nextTime - currentTime;
                            
                            if (timeDiff <= 0) {
                                // 如果已经过了时间，立即发送
                                sendLyric(client, currentLine.getText());
                                currentIndex++;
                                // 继续调度下一句
                                scheduleNextLyric(client, 0);
                            } else {
                                // 等待到正确的时间
                                timer = new Timer();
                                timer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        if (isPlaying && !isPaused && client != null) {
                                            client.execute(() -> {
                                                if (currentLyrics != null && currentIndex < currentLyrics.getLines().size()) {
                                                    LyricLine line = currentLyrics.getLines().get(currentIndex);
                                                    sendLyric(client, line.getText());
                                                    currentIndex++;
                                                    // 继续调度下一句
                                                    scheduleNextLyric(client, 0);
                                                } else {
                                                    // 播放完成
                                                    stopPlayback();
                                                }
                                            });
                                        }
                                    }
                                }, timeDiff);
                            }
                        } else {
                            // 播放完成
                            stopPlayback();
                        }
                    });
                }
            }
        }, Math.max(0, delay));
    }

    private void pause() {
        if (isPlaying && !isPaused) {
            isPaused = true;
            pauseTime = System.currentTimeMillis();
            stopTimer();
        }
    }

    private void resume(Minecraft client) {
        if (isPlaying && isPaused) {
            isPaused = false;
            // 计算已经播放的时间
            long elapsedTime = pauseTime - startTime;
            startTime = System.currentTimeMillis() - elapsedTime;
            
            // 继续播放
            if (currentIndex < currentLyrics.getLines().size()) {
                LyricLine nextLine = currentLyrics.getLines().get(currentIndex);
                long nextTime = nextLine.getTime();
                long currentTime = System.currentTimeMillis() - startTime;
                long timeDiff = nextTime - currentTime;
                
                if (timeDiff <= 0) {
                    // 如果已经过了时间，立即发送
                    sendLyric(client, nextLine.getText());
                    currentIndex++;
                    scheduleNextLyric(client, 0);
                } else {
                    scheduleNextLyric(client, timeDiff);
                }
            }
        }
    }

    // 停止播放
    public void stopPlayback() {
        isPlaying = false;
        isPaused = false;
        stopTimer();
        currentIndex = 0;
        // 发送停止提示
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(
                Component.literal("§c[歌词] §f已停止播放")
            );
        }
    }

    // ↑键播放下一句（开启自动播放）
    public void playNextLyric(Minecraft client) {
        if (currentLyrics == null || currentLyrics.getLines().isEmpty()) {
            return;
        }

        // 如果正在播放，停止播放
        if (isPlaying) {
            stopPlayback();
            // 发送停止提示
            if (client.player != null) {
                client.player.sendSystemMessage(
                    Component.literal("§c[歌词] §f已停止播放")
                );
            }
            return;
        }

        // 如果当前没有播放，从当前索引开始自动播放
        startPlayback(client);
    }

    private void sendLyric(Minecraft client, String lyric) {
        LocalPlayer player = client.player;
        if (player != null) {
            // 发送聊天消息
            player.sendSystemMessage(Component.literal("§b[歌词] §f" + lyric));
        }
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void togglePlay() {
        if (isPlaying && !isPaused) {
            pause();
        } else if (isPaused) {
            // 通过play方法恢复
            isPaused = false;
            isPlaying = true;
        } else {
            isPlaying = true;
        }
    }

    public void clear() {
        // 不清除列表，只重置状态
        currentLyrics = null;
        currentIndex = 0;
        isPlaying = false;
        isPaused = false;
        stopTimer();
    }

    // 歌词文件类
    public static class LyricsFile {
        private final String title;
        private final List<LyricLine> lines;

        public LyricsFile(String title, List<LyricLine> lines) {
            this.title = title;
            this.lines = lines;
        }

        public String getTitle() { return title; }
        public List<LyricLine> getLines() { return lines; }
    }

    // 歌词行类
    public static class LyricLine {
        private final long time;
        private final String text;

        public LyricLine(long time, String text) {
            this.time = time;
            this.text = text;
        }

        public long getTime() { return time; }
        public String getText() { return text; }
    }
}