package com.minerva.api.personality;

import java.io.IOException;
import java.util.List;

/**
 * Extracts a bounded set of still frames from a supported video asset.
 */
public interface VideoFrameExtractor {
    /**
     * Extracts image frames from a video for visual analysis.
     *
     * @param video fetched video asset
     * @return frame image bytes in presentation order
     * @throws IllegalArgumentException if the video format is unsupported
     * @throws IOException if the video cannot be decoded or extraction limits are exceeded
     */
    List<byte[]> extract(RemoteMediaFetcher.MediaAsset video) throws IOException;
}
