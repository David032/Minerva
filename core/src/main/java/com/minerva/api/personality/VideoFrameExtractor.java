package com.minerva.api.personality;

import java.io.IOException;
import java.util.List;

public interface VideoFrameExtractor {
    List<byte[]> extract(RemoteMediaFetcher.MediaAsset video) throws IOException;
}
