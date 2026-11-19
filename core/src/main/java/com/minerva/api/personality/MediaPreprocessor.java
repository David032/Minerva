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
     * @throws IllegalArgumentException if more than 12 images would be sent in one model request
     * @throws IOException if an animated image cannot be decoded or prepared
     */
    public List<RemoteMediaFetcher.MediaAsset> prepare(List<RemoteMediaFetcher.MediaAsset> assets) throws IOException {
        List<List<RemoteMediaFetcher.MediaAsset>> batches = prepareBatches(assets);
        if (batches.size() > 1) {
            throw new IllegalArgumentException("This upload contains too many images or video frames to analyze at once.");
        }
        return batches.isEmpty() ? List.of() : batches.getFirst();
    }

    /**
     * Prepares media and groups the resulting images into requests accepted by the local model.
     *
     * @param assets fetched media assets
     * @return immutable image batches, each containing no more than 12 images
     * @throws IOException if animated media cannot be decoded or prepared
     */
    public List<List<RemoteMediaFetcher.MediaAsset>> prepareBatches(
            List<RemoteMediaFetcher.MediaAsset> assets
    ) throws IOException {
        List<List<RemoteMediaFetcher.MediaAsset>> batches = new ArrayList<>();
        List<RemoteMediaFetcher.MediaAsset> batch = new ArrayList<>(MAX_MODEL_IMAGES);
        for (RemoteMediaFetcher.MediaAsset asset : assets) {
            List<RemoteMediaFetcher.MediaAsset> preparedAssets;
            if (asset.contentType().startsWith("video/")) {
                preparedAssets = videoFrameExtractor.extract(asset).stream()
                        .map(frame -> new RemoteMediaFetcher.MediaAsset(asset.source(), "image/png", frame))
                        .toList();
            } else if (asset.contentType().equals("image/gif")) {
                preparedAssets = List.of(new RemoteMediaFetcher.MediaAsset(
                        asset.source(), "image/png", firstGifFrame(asset)));
            } else {
                preparedAssets = List.of(asset);
            }
            for (RemoteMediaFetcher.MediaAsset preparedAsset : preparedAssets) {
                if (batch.size() == MAX_MODEL_IMAGES) {
                    batches.add(List.copyOf(batch));
                    batch.clear();
                }
                batch.add(preparedAsset);
            }
        }
        if (!batch.isEmpty()) {
            batches.add(List.copyOf(batch));
        }
        return List.copyOf(batches);
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
