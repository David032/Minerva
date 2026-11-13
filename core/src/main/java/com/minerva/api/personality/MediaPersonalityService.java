package com.minerva.api.personality;

import com.minerva.core.personality.PersonalityPromptStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@Service
public class MediaPersonalityService {
    private static final long MAX_IMAGE_UPLOAD_BYTES = MediaDocumentExtractor.MAX_UPLOAD_BYTES;
    private static final String DEFAULT_IMAGE_REQUEST = "What is this image about?";
    private final MediaDocumentExtractor extractor;
    private final RemoteMediaFetcher mediaFetcher;
    private final MediaPreprocessor mediaPreprocessor;
    private final PersonalityAnalyzer analyzer;
    private final PersonalityImageOpinionService imageOpinionService;
    private final PersonalityPromptStore promptStore;
    private final InterestMemoryStore memoryStore;

    public MediaPersonalityService(
            MediaDocumentExtractor extractor,
            RemoteMediaFetcher mediaFetcher,
            MediaPreprocessor mediaPreprocessor,
            PersonalityAnalyzer analyzer,
            PersonalityImageOpinionService imageOpinionService,
            PersonalityPromptStore promptStore,
            InterestMemoryStore memoryStore
    ) {
        this.extractor = extractor;
        this.mediaFetcher = mediaFetcher;
        this.mediaPreprocessor = mediaPreprocessor;
        this.analyzer = analyzer;
        this.imageOpinionService = imageOpinionService;
        this.promptStore = promptStore;
        this.memoryStore = memoryStore;
    }

    public synchronized SubmissionResult process(MultipartFile file, String guidance) throws IOException {
        MediaDocumentExtractor.Extraction extraction = extractor.extract(file);
        List<RemoteMediaFetcher.MediaAsset> fetchedMedia = mediaFetcher.fetch(extraction.mediaUrls());
        List<RemoteMediaFetcher.MediaAsset> media = mediaPreprocessor.prepare(fetchedMedia);
        return analyzeAndSave(extraction, media, guidance, extraction.mediaUrls().size());
    }

    public synchronized SubmissionResult processImage(
            MultipartFile imageFile,
            String imageUrl,
            String comment
    ) throws IOException {
        boolean hasUpload = imageFile != null && !imageFile.isEmpty();
        boolean hasUrl = imageUrl != null && !imageUrl.isBlank();
        if (hasUpload == hasUrl) {
            throw new IllegalArgumentException("Choose either an image file or an image URL.");
        }

        List<RemoteMediaFetcher.MediaAsset> images;
        if (hasUpload) {
            images = List.of(uploadedImage(imageFile));
        } else {
            URI uri;
            try {
                uri = URI.create(imageUrl.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Enter a valid image URL.", e);
            }
            images = mediaFetcher.fetch(List.of(uri));
        }
        if (images.size() != 1 || !images.getFirst().contentType().startsWith("image/")) {
            throw new IllegalArgumentException("The selected URL must point to a supported image.");
        }
        List<RemoteMediaFetcher.MediaAsset> media = mediaPreprocessor.prepare(images);

        String effectiveComment = comment == null ? "" : comment.trim();
        String imageRequest = effectiveComment.isEmpty() ? DEFAULT_IMAGE_REQUEST : effectiveComment;
        String description;
        try {
            description = imageOpinionService.analyze(media.getFirst(), imageRequest);
        } catch (RuntimeException e) {
            throw new IOException("Image analysis failed; the saved personality prompt was not changed.", e);
        }
        if (description == null || description.isBlank()) {
            throw new IOException("Image analysis returned no description; the saved personality prompt was not changed.");
        }
        var extraction = new MediaDocumentExtractor.Extraction("Image analysis:\n" + description, List.of(), List.of());
        return analyzeAndSave(extraction, List.of(), effectiveComment, 1);
    }

    private RemoteMediaFetcher.MediaAsset uploadedImage(MultipartFile file) throws IOException {
        if (file.getSize() > MAX_IMAGE_UPLOAD_BYTES) {
            throw new IllegalArgumentException("The uploaded image exceeds the 10 MB limit.");
        }
        byte[] content = file.getBytes();
        String contentType = imageContentType(content);
        return new RemoteMediaFetcher.MediaAsset(URI.create("upload://image"), contentType, content);
    }

    private static String imageContentType(byte[] content) {
        if (content.length >= 3 && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (content.length >= 8 && (content[0] & 0xff) == 0x89
                && content[1] == 'P' && content[2] == 'N' && content[3] == 'G'
                && content[4] == 13 && content[5] == 10 && content[6] == 26 && content[7] == 10) {
            return "image/png";
        }
        if (content.length >= 6) {
            String signature = new String(content, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
            if (signature.equals("GIF87a") || signature.equals("GIF89a")) {
                return "image/gif";
            }
        }
        if (content.length >= 12 && new String(content, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(content, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) {
            return "image/webp";
        }
        throw new IllegalArgumentException("The uploaded file is not a supported JPEG, PNG, GIF, or WebP image.");
    }

    private SubmissionResult analyzeAndSave(
            MediaDocumentExtractor.Extraction extraction,
            List<RemoteMediaFetcher.MediaAsset> media,
            String guidance,
            int analyzedMediaCount
    ) throws IOException {
        String currentPrompt = promptStore.load();
        String updatedPrompt;
        try {
            updatedPrompt = analyzer.analyze(extraction, media, currentPrompt, guidance);
        } catch (RuntimeException e) {
            throw new IOException("Media analysis failed; the saved personality prompt was not changed.", e);
        }

        byte[] oldPrompt = promptStore.snapshot();
        byte[] oldMemories = memoryStore.snapshot();
        try {
            memoryStore.addAll(extraction.tags());
            promptStore.save(updatedPrompt);
        } catch (IOException | RuntimeException e) {
            try {
                promptStore.restore(oldPrompt);
                memoryStore.restore(oldMemories);
            } catch (IOException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        }
        return new SubmissionResult(extraction.tags().size(), analyzedMediaCount, updatedPrompt);
    }

    public synchronized String loadPrompt() throws IOException {
        return promptStore.load();
    }

    public synchronized void savePrompt(String prompt) throws IOException {
        promptStore.save(prompt);
    }

    public synchronized void resetPrompt() throws IOException {
        promptStore.reset();
    }

    public record SubmissionResult(int savedTagCount, int analyzedMediaCount, String personalityPrompt) {
    }
}
