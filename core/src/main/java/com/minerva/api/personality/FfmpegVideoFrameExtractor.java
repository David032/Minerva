package com.minerva.api.personality;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class FfmpegVideoFrameExtractor implements VideoFrameExtractor {
    private static final long MAX_FRAME_BYTES = 2L * 1024 * 1024;
    private static final long MAX_OUTPUT_BYTES = 6L * 1024 * 1024;
    private final String ffmpegCommand;

    public FfmpegVideoFrameExtractor(@Value("${minerva.media.ffmpeg-command:ffmpeg}") String ffmpegCommand) {
        this.ffmpegCommand = ffmpegCommand;
    }

    @Override
    public List<byte[]> extract(RemoteMediaFetcher.MediaAsset video) throws IOException {
        String extension = switch (video.contentType()) {
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/ogg" -> ".ogv";
            default -> throw new IllegalArgumentException("This video format cannot be analyzed.");
        };
        Path directory = Files.createTempDirectory("minerva-video-");
        Process process = null;
        try {
            Path outputPattern = directory.resolve("frame-%02d.png");
            try {
                process = new ProcessBuilder(
                        ffmpegCommand,
                        "-nostdin", "-hide_banner", "-loglevel", "error",
                        "-threads", "1", "-max_alloc", "67108864",
                        "-max_pixels", "16000000",
                        "-protocol_whitelist", "pipe",
                        "-f", extension.equals(".mp4") ? "mp4" : extension.equals(".webm") ? "webm" : "ogg",
                        "-i", "pipe:0",
                        "-t", "30",
                        "-vf", "fps=1/10,scale=1024:1024:force_original_aspect_ratio=decrease",
                        "-frames:v", "3", "-an", "-sn", "-dn",
                        "-protocol_whitelist", "file",
                        "-f", "image2", outputPattern.toString()
                ).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            } catch (IOException e) {
                throw new IOException("Video analysis requires FFmpeg to be installed and available on PATH.", e);
            }

            IOException inputFailure = null;
            try {
                process.getOutputStream().write(video.content());
            } catch (IOException e) {
                inputFailure = e;
            } finally {
                try {
                    process.getOutputStream().close();
                } catch (IOException e) {
                    if (inputFailure == null) {
                        inputFailure = e;
                    }
                }
            }
            try {
                if (!process.waitFor(20, TimeUnit.SECONDS)) {
                    terminate(process);
                    throw new IOException("Video frame extraction exceeded its 20-second limit.");
                }
            } catch (InterruptedException e) {
                terminate(process);
                Thread.currentThread().interrupt();
                throw new IOException("Video frame extraction was interrupted.", e);
            }
            if (process.exitValue() != 0) {
                var failure = new IOException("The referenced video could not be decoded for analysis.");
                if (inputFailure != null) {
                    failure.addSuppressed(inputFailure);
                }
                throw failure;
            }

            try (Stream<Path> files = Files.list(directory)) {
                List<Path> frames = files
                        .filter(path -> path.getFileName().toString().matches("frame-\\d{2}\\.png"))
                        .sorted(Comparator.comparing(Path::getFileName))
                        .toList();
                if (frames.isEmpty()) {
                    throw new IOException("No video frames could be extracted for analysis.");
                }
                long totalBytes = 0;
                var content = new java.util.ArrayList<byte[]>(frames.size());
                for (Path frame : frames) {
                    long size = Files.size(frame);
                    totalBytes += size;
                    if (size > MAX_FRAME_BYTES || totalBytes > MAX_OUTPUT_BYTES) {
                        throw new IOException("Extracted video frames exceed the analysis size limit.");
                    }
                    content.add(Files.readAllBytes(frame));
                }
                return List.copyOf(content);
            }
        } finally {
            if (process != null && process.isAlive()) {
                terminate(process);
            }
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void terminate(Process process) {
        process.destroyForcibly();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
