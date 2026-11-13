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

@Component
public class MediaPreprocessor {
    private static final int MAX_MODEL_IMAGES = 12;
    private static final int MAX_IMAGE_PIXELS = 4_000_000;
    private static final long MAX_MODEL_MEDIA_BYTES = 20L * 1024 * 1024;
    private final VideoFrameExtractor videoFrameExtractor;

    public MediaPreprocessor(VideoFrameExtractor videoFrameExtractor) {
        this.videoFrameExtractor = videoFrameExtractor;
    }

    public List<RemoteMediaFetcher.MediaAsset> prepare(List<RemoteMediaFetcher.MediaAsset> assets) throws IOException {
        List<RemoteMediaFetcher.MediaAsset> prepared = new ArrayList<>();
        long totalBytes = 0;
        for (RemoteMediaFetcher.MediaAsset asset : assets) {
            int previousSize = prepared.size();
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
            for (int i = previousSize; i < prepared.size(); i++) {
                totalBytes += prepared.get(i).content().length;
            }
            if (totalBytes > MAX_MODEL_MEDIA_BYTES) {
                throw new IllegalArgumentException("Prepared media exceeds the 20 MB total analysis limit.");
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
                    if (output.size() > 5 * 1024 * 1024) {
                        throw new IOException("The prepared GIF frame exceeds the 5 MB analysis limit.");
                    }
                    return output.toByteArray();
                }
            } finally {
                reader.dispose();
            }
        }
    }
}
