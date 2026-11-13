package com.minerva.api.personality;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("Unit")
class MediaPreprocessorTests {
    @Test
    void convertsAnimatedMediaInputsToImageFramesForTheLocalModel() throws Exception {
        byte[] gif = createGif();
        byte[] frame = new byte[] {1, 2, 3};
        var source = URI.create("https://media.example.test/media");
        var preprocessor = new MediaPreprocessor(video -> List.of(frame, frame));

        var gifFrames = preprocessor.prepare(List.of(
                new RemoteMediaFetcher.MediaAsset(source, "image/gif", gif)));
        var videoFrames = preprocessor.prepare(List.of(
                new RemoteMediaFetcher.MediaAsset(source, "video/mp4", new byte[] {4, 5})));

        assertEquals("image/png", gifFrames.getFirst().contentType());
        assertTrue(gifFrames.getFirst().content().length > 0);
        assertEquals(2, videoFrames.size());
        assertTrue(videoFrames.stream().allMatch(asset -> asset.contentType().equals("image/png")));
        assertNotEquals("video/mp4", videoFrames.getFirst().contentType());
    }

    @Test
    void rejectsTooManyImagesForOneModelRequest() {
        var assets = new ArrayList<RemoteMediaFetcher.MediaAsset>();
        var source = URI.create("https://media.example.test/image.png");
        for (int i = 0; i < 13; i++) {
            assets.add(new RemoteMediaFetcher.MediaAsset(source, "image/png", new byte[] {1}));
        }
        var preprocessor = new MediaPreprocessor(video -> List.of());

        assertThrows(IllegalArgumentException.class, () -> preprocessor.prepare(assets));
    }

    @Test
    void rejectsPreparedImagesOverFiveMegabytes() {
        var image = new RemoteMediaFetcher.MediaAsset(
                URI.create("upload://image"), "image/png", new byte[5 * 1024 * 1024 + 1]);

        assertThrows(IllegalArgumentException.class,
                () -> new MediaPreprocessor(video -> List.of()).prepare(List.of(image)));
    }

    @Test
    void extractsFramesFromTheBundledVideoWhenFfmpegIsAvailable() throws Exception {
        Assumptions.assumeTrue(ffmpegAvailable(), "FFmpeg is required for this fixture test.");
        byte[] video;
        try (var input = new ClassPathResource("731543319053778944_0.mp4").getInputStream()) {
            video = input.readAllBytes();
        }
        var source = URI.create("https://media.example.test/731543319053778944_0.mp4");
        var extractor = new FfmpegVideoFrameExtractor("ffmpeg");

        List<byte[]> frames = extractor.extract(
                new RemoteMediaFetcher.MediaAsset(source, "video/mp4", video));

        assertFalse(frames.isEmpty());
        assertTrue(frames.size() <= 3);
        for (byte[] frame : frames) {
            assertTrue(ImageIO.read(new java.io.ByteArrayInputStream(frame)) != null);
        }
    }

    private static boolean ffmpegAvailable() throws IOException, InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            return false;
        }
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static byte[] createGif() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "gif", output);
            return output.toByteArray();
        }
    }
}
