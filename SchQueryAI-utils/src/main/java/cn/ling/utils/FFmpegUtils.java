package cn.ling.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * FFmpeg音频转换工具类
 * 负责将上传的音频文件转换为指定格式的PCM音频数据
 * 使用FFmpeg工具进行音频格式转换，支持多种音频格式输入
 */
@Slf4j // 启用SLF4J日志功能
@Component
public class FFmpegUtils {

    // 目标PCM音频的采样率：16000Hz
    private static final int AUDIO_SAMPLE_RATE = 16000;

    /**
     * 将上传的音频文件转换为PCM格式
     * 转换参数：16kHz采样率、单声道、16bit位深
     *
     * @param file 上传的音频文件
     * @return 转换后的PCM音频字节数组
     * @throws Exception 转换过程中发生的异常
     */
    public byte[] convertAudioToPcm(MultipartFile file) throws Exception {
        log.info("开始音频转换为PCM格式，原始文件名: {}", file.getOriginalFilename());
        log.debug("原始文件大小: {} bytes, 内容类型: {}", file.getSize(), file.getContentType());

        // 创建临时文件（避免内存溢出，JVM退出后自动删除）
        // 输入临时文件：保存上传的原始音频
        File tempInput = File.createTempFile("audio_input_", getFileExtension(file.getOriginalFilename()));
        // 输出临时文件：保存转换后的PCM音频
        File tempOutput = File.createTempFile("audio_output_", ".pcm");
        tempInput.deleteOnExit();
        tempOutput.deleteOnExit();

        log.debug("创建临时输入文件: {}, 临时输出文件: {}", tempInput.getAbsolutePath(), tempOutput.getAbsolutePath());

        try {
            // 写入上传文件到临时输入文件
            log.debug("开始将上传文件内容写入临时文件");
            try (InputStream in = file.getInputStream();
                 OutputStream out = new FileOutputStream(tempInput)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
            log.debug("上传文件内容写入临时文件完成");

            // 构建FFmpeg转换命令
            // 参数说明：
            // -y: 覆盖输出文件
            // -i: 输入文件
            // -ar: 音频采样率
            // -ac: 声道数（1=单声道）
            // -acodec pcm_s16le: 音频编码格式（16位little-endian PCM）
            // -f s16le: 输出格式（16位little-endian）
            // -loglevel warning: 日志级别（仅显示警告及以上信息）
            String ffmpegCmd = String.format(
                    "ffmpeg -y -i %s -ar %d -ac 1 -acodec pcm_s16le -f s16le %s -loglevel warning",
                    tempInput.getAbsolutePath(), AUDIO_SAMPLE_RATE, tempOutput.getAbsolutePath()
            );
            log.info("执行FFmpeg转换命令: {}", ffmpegCmd);

            // 执行FFmpeg命令
            Process process = Runtime.getRuntime().exec(ffmpegCmd);

            // 异步读取FFmpeg输出流和错误流（避免进程阻塞）
            readProcessOutput(process.getInputStream(), "FFmpeg输出");
            readProcessOutput(process.getErrorStream(), "FFmpeg错误");

            // 等待转换完成（超时30秒）
            log.debug("等待FFmpeg转换完成，超时时间30秒");
            boolean isCompleted = process.waitFor(30, TimeUnit.SECONDS);
            if (!isCompleted) {
                process.destroyForcibly();
                log.error("FFmpeg转换超时（30秒）");
                throw new Exception("FFmpeg转换超时（30秒）");
            }

            // 检查转换结果
            int exitValue = process.exitValue();
            if (exitValue != 0) {
                log.error("FFmpeg转换失败，退出码: {}", exitValue);
                throw new Exception("FFmpeg转换失败（退出码：" + exitValue + "）");
            }

            // 验证输出文件是否存在且不为空
            if (!tempOutput.exists() || tempOutput.length() == 0) {
                log.error("FFmpeg转换成功但未生成有效输出文件");
                throw new Exception("FFmpeg转换成功但未生成有效输出文件");
            }

            // 读取PCM数据
            log.debug("开始读取转换后的PCM文件，文件大小: {} bytes", tempOutput.length());
            byte[] pcmData = Files.readAllBytes(tempOutput.toPath());
            log.info("PCM转换完成，转换后数据大小: {} bytes", pcmData.length);

            return pcmData;
        } finally {
            // 清理临时文件
            if (tempInput.delete()) {
                log.debug("临时输入文件已删除: {}", tempInput.getAbsolutePath());
            } else {
                log.warn("无法删除临时输入文件: {}", tempInput.getAbsolutePath());
            }
            if (tempOutput.delete()) {
                log.debug("临时输出文件已删除: {}", tempOutput.getAbsolutePath());
            } else {
                log.warn("无法删除临时输出文件: {}", tempOutput.getAbsolutePath());
            }
        }
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 文件扩展名（包含点号），默认.wav
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            log.debug("文件名不存在或没有扩展名，使用默认扩展名.wav");
            return ".wav";
        }
        String extension = filename.substring(filename.lastIndexOf("."));
        log.debug("获取到文件扩展名: {}", extension);
        return extension;
    }

    /**
     * 异步读取进程输出流，防止进程阻塞
     *
     * @param inputStream 进程的输入流（实际是进程的输出）
     * @param streamName 流名称，用于日志标识
     */
    private void readProcessOutput(InputStream inputStream, String streamName) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 记录FFmpeg输出的详细信息到调试日志
                    log.debug("{}: {}", streamName, line);
                }
            } catch (Exception e) {
                log.warn("读取{}时发生错误", streamName, e);
            }
        }, "FFmpeg-" + streamName + "-Reader").start();
    }
}

