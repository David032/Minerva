package com.minerva.api.personality;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private static byte[] createGif() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "gif", output);
            return output.toByteArray();
        }
    }
}
