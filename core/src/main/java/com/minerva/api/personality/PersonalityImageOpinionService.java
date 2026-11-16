package com.minerva.api.personality;

import com.minerva.core.ConversationManager;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Obtains a model-generated opinion or description for one supported image asset.
 */
@Component
public class PersonalityImageOpinionService {
    /**
     * Analyzes an image using the shared conversation model and removes its temporary file afterward.
     *
     * @param image image bytes and content type
     * @param request the user's question or comment about the image
     * @return the model's description or opinion
     * @throws IllegalArgumentException if the image format is unsupported
     * @throws IOException if the image cannot be staged or analyzed
     */
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
