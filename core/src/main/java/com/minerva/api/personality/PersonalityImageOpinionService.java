package com.minerva.api.personality;

import com.minerva.core.ConversationManager;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;

@Component
public class PersonalityImageOpinionService {
    public String analyze(RemoteMediaFetcher.MediaAsset image, String request) throws IOException {
        String extension = switch (image.contentType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("Only JPEG, PNG, GIF, and WebP images are supported.");
        };
        var imagePath = Files.createTempFile("minerva-personality-image-", extension);
        try {
            Files.write(imagePath, image.content());
            return ConversationManager.seekImageOpinion(
                    imagePath.toAbsolutePath().toString(), request, image.mimeType());
        } finally {
            Files.deleteIfExists(imagePath);
        }
    }
}
