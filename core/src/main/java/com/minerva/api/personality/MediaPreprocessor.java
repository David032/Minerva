package com.minerva.api.personality;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Converts fetched media into image assets that can be analysed by the local model.
 */
@Component
public class MediaPreprocessor {
    private static final int MAX_MODEL_IMAGES = 12;
    private static final int MAX_IMAGE_PIXELS = 4_000_000;
    private final VideoFrameExtractor videoFrameExtractor;

    /**
     * Creates the preprocessor with its video-frame extraction strategy.
     *
     * @param videoFrameExtractor extracts bounded image frames from supported videos
     */
    public MediaPreprocessor(VideoFrameExtractor videoFrameExtractor) {
        this.videoFrameExtractor = videoFrameExtractor;
    }

    /**
     * Prepares images, GIFs, and videos for model analysis.
     *
     * @param assets fetched media assets
     * @return an immutable list of prepared images and sampled video frames
     * @throws IOException if an animated image cannot be decoded or prepared
     */
    public List<RemoteMediaFetcher.MediaAsset> prepare(List<RemoteMediaFetcher.MediaAsset> assets) throws IOException {
        List<RemoteMediaFetcher.MediaAsset> prepared = new ArrayList<>();
        for (RemoteMediaFetcher.MediaAsset asset : assets) {
            if (asset.contentType().startsWith("video/")) {
                for (byte[] frame : videoFrameExtractor.extract(asset)) {
                    prepared.add(new RemoteMediaFetcher.MediaAsset(asset.source(), "image/png", frame));
                }
            } else if (asset.contentType().equals("image/gif")) {
                prepared.add(new RemoteMediaFetcher.MediaAsset(asset.source(), "image/png", firstGifFrame(asset)));
            } else {
                prepared.add(asset);
            }
            if (prepared.size() > MAX_MODEL_IMAGES) {
                throw new IllegalArgumentException("This upload contains too many images or video frames to analyze at once.");
            }
        }
        return List.copyOf(prepared);
    }

    private static byte[] firstGifFrame(RemoteMediaFetcher.MediaAsset gif) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(gif.content()))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("The referenced GIF could not be decoded.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IOException("The referenced GIF frame exceeds the image size limit.");
                }
                var frame = reader.read(0);
                try (var output = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(frame, "png", output)) {
                        throw new IOException("The GIF frame could not be prepared for model analysis.");
                    }
                    return output.toByteArray();
                }
            } finally {
                reader.dispose();
            }
        }
    }
}
