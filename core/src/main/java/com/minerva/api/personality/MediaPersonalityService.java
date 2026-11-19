package com.minerva.api.personality;

import com.minerva.core.AiBusyException;
import com.minerva.core.personality.PersonalityPromptStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Coordinates media extraction, analysis, persistence, and user-managed personality preferences.
 */
@Service
public class MediaPersonalityService {
    private static final int MAX_BATCH_FILES = 50;
    private static final Logger logger = LoggerFactory.getLogger(MediaPersonalityService.class);
    private static final String DEFAULT_IMAGE_REQUEST = "What is this image about?";
    private final MediaDocumentExtractor extractor;
    private final RemoteMediaFetcher mediaFetcher;
    private final MediaPreprocessor mediaPreprocessor;
    private final PersonalityAnalyzer analyzer;
    private final PersonalityImageOpinionService imageOpinionService;
    private final PersonalityPromptStore promptStore;
    private final InterestMemoryStore memoryStore;

    /**
     * Creates a media personality workflow with its extraction, analysis, and storage collaborators.
     *
     * @param extractor extracts text, tags, and media references from documents
     * @param mediaFetcher retrieves eligible media referenced by documents
     * @param mediaPreprocessor prepares fetched media for model analysis
     * @param analyzer updates the user's personality preferences
     * @param imageOpinionService describes or assesses uploaded images
     * @param promptStore persists the personality prompt
     * @param memoryStore persists media-inferred and user-scored interests
     */
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

    /**
     * Extracts and analyses a PDF or HTML upload, then saves its resulting preferences and tags.
     *
     * @param file the PDF or HTML document to process
     * @param guidance optional explicit preference guidance from the user
     * @return counts and the resulting personality prompt
     * @throws IllegalArgumentException if the upload is empty or unsupported
     * @throws IOException if extraction, media retrieval, analysis, or persistence fails
     */
    public synchronized SubmissionResult process(MultipartFile file, String guidance) throws IOException {
        MediaDocumentExtractor.Extraction extraction = extractor.extract(file);
        List<RemoteMediaFetcher.MediaAsset> fetchedMedia = mediaFetcher.fetch(extraction.mediaUrls());
        List<List<RemoteMediaFetcher.MediaAsset>> mediaBatches = mediaPreprocessor.prepareBatches(fetchedMedia);
        return analyzeAndSave(extraction, mediaBatches, guidance, extraction.mediaUrls().size());
    }

    /**
     * Analyses fetched article text with the user's comment as explicit preference guidance.
     *
     * @param article fetched and parsed article content
     * @param comment the user's explicit preference guidance
     * @return counts and the resulting personality prompt
     * @throws IOException if analysis or persistence fails
     */
    public synchronized SubmissionResult processArticle(RemoteArticleFetcher.Article article, String comment)
            throws IOException {
        String articleText = (article.title().isBlank() ? "" : "Title: " + article.title() + "\n")
                + "Source: " + article.source() + "\n" + article.text();
        var extraction = new MediaDocumentExtractor.Extraction(articleText, List.of(), List.of(), List.of());
        return analyzeAndSave(extraction, List.of(), comment, 0);
    }

    /**
     * Processes a batch of HTML files independently, retaining the filename and error for each failure.
     *
     * @param files HTML files selected for batch processing
     * @param guidance optional explicit preference guidance applied to each file
     * @return aggregate counts, per-file failures, and the latest personality prompt
     * @throws IllegalArgumentException if no non-empty files were selected
     * @throws IOException if the latest prompt cannot be loaded after processing
     */
    public synchronized BatchSubmissionResult processHtmlBatch(List<MultipartFile> files, String guidance)
            throws IOException {
        validateBatchFileCount(files);
        if (files == null || files.stream().noneMatch(file -> file != null && !file.isEmpty())) {
            throw new IllegalArgumentException("Choose at least one HTML file.");
        }

        int successfulFileCount = 0;
        int savedTagCount = 0;
        int analyzedMediaCount = 0;
        List<FileFailure> failures = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String filename = displayFilename(file.getOriginalFilename());
            try {
                if (!isHtmlFilename(file.getOriginalFilename())) {
                    throw new IllegalArgumentException("Only HTML files are supported in a batch.");
                }
                SubmissionResult result = process(file, guidance);
                successfulFileCount++;
                savedTagCount += result.savedTagCount();
                analyzedMediaCount += result.analyzedMediaCount();
            } catch (IllegalArgumentException | IOException e) {
                failures.add(new FileFailure(filename, e.getMessage() == null ? "Processing failed." : e.getMessage()));
            }
        }
        return new BatchSubmissionResult(
                successfulFileCount, savedTagCount, analyzedMediaCount, failures, promptStore.load());
    }

    /**
     * Processes uploaded images independently, preserving the filename and error for each failure.
     *
     * @param files images selected for batch processing
     * @param comment optional image-analysis comment applied to each image
     * @return aggregate counts, per-file failures, and the latest personality prompt
     * @throws IllegalArgumentException if no non-empty images were selected
     * @throws IOException if the latest prompt cannot be loaded after processing
     */
    public synchronized BatchSubmissionResult processImageBatch(List<MultipartFile> files, String comment)
            throws IOException {
        validateBatchFileCount(files);
        if (files == null || files.stream().noneMatch(file -> file != null && !file.isEmpty())) {
            throw new IllegalArgumentException("Choose at least one image.");
        }

        int successfulFileCount = 0;
        int savedTagCount = 0;
        int analyzedMediaCount = 0;
        List<FileFailure> failures = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String filename = displayFilename(file.getOriginalFilename());
            try {
                SubmissionResult result = processImage(file, null, comment);
                successfulFileCount++;
                savedTagCount += result.savedTagCount();
                analyzedMediaCount += result.analyzedMediaCount();
            } catch (IllegalArgumentException | IOException e) {
                failures.add(new FileFailure(filename, e.getMessage() == null ? "Processing failed." : e.getMessage()));
            }
        }
        return new BatchSubmissionResult(
                successfulFileCount, savedTagCount, analyzedMediaCount, failures, promptStore.load());
    }

    /**
     * Analyses an uploaded image or image URL and uses the resulting description as preference context.
     *
     * @param imageFile an optional uploaded image
     * @param imageUrl an optional remote image URL
     * @param comment optional user guidance about the image
     * @return counts and the resulting personality prompt
     * @throws IllegalArgumentException if neither or both image sources are supplied, or the image is unsupported
     * @throws IOException if image analysis, media retrieval, or persistence fails
     */
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
        } catch (AiBusyException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Image analysis failed; the saved personality prompt was not changed.", e);
        }
        if (description == null || description.isBlank()) {
            throw new IOException("Image analysis returned no description; the saved personality prompt was not changed.");
        }
        var extraction = new MediaDocumentExtractor.Extraction(
                "Image analysis:\n" + description, List.of(), List.of(), List.of());
        return analyzeAndSave(extraction, List.of(), effectiveComment, 1);
    }

    /**
     * Analyses a public image URL and applies the result to the saved personality prompt.
     *
     * @param imageUrl public HTTP(S) image URL
     * @param comment optional guidance about the image
     * @return counts and the resulting personality prompt
     * @throws IOException if image retrieval or personality analysis fails
     */
    @Tool(description = "Analyze a public image URL and conservatively update Minerva's saved personality preferences")
    public synchronized SubmissionResult processImageUrl(String imageUrl, String comment) throws IOException {
        return processImage(null, imageUrl, comment);
    }

    private RemoteMediaFetcher.MediaAsset uploadedImage(MultipartFile file) throws IOException {
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
            List<List<RemoteMediaFetcher.MediaAsset>> mediaBatches,
            String guidance,
            int analyzedMediaCount
    ) throws IOException {
        String currentPrompt = promptStore.load();
        String updatedPrompt = currentPrompt;
        try {
            List<List<RemoteMediaFetcher.MediaAsset>> batches = mediaBatches.isEmpty()
                    ? List.of(List.of())
                    : mediaBatches;
            for (List<RemoteMediaFetcher.MediaAsset> media : batches) {
                updatedPrompt = analyzer.analyze(extraction, media, updatedPrompt, guidance);
            }
        } catch (AiBusyException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Media analysis failed; the saved personality prompt was not changed.", e);
        }

        byte[] oldPrompt = promptStore.snapshot();
        byte[] oldMemories = memoryStore.snapshot();
        List<String> addedTags;
        try {
            promptStore.save(updatedPrompt);
            addedTags = memoryStore.addAll(extraction.tags());
        } catch (IOException | RuntimeException e) {
            try {
                promptStore.restore(oldPrompt);
                memoryStore.restore(oldMemories);
            } catch (IOException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        }
        for (String tag : addedTags) {
            logger.info("Added extracted interest at neutral score 3/5: {}", sanitiseLogValue(tag));
        }
        String additions = promptAdditions(currentPrompt, updatedPrompt);
        if (!additions.isBlank()) {
            logger.info("Added to personality prompt: {}", sanitiseLogValue(additions));
        }
        return new SubmissionResult(addedTags.size(), analyzedMediaCount, updatedPrompt);
    }

    private static void validateBatchFileCount(List<MultipartFile> files) {
        long uploadedFileCount = files == null ? 0
                : files.stream().filter(file -> file != null
                        && (!file.isEmpty() || file.getOriginalFilename() != null
                        && !file.getOriginalFilename().isBlank())).count();
        if (uploadedFileCount > MAX_BATCH_FILES) {
            throw new IllegalArgumentException("A maximum of 50 files can be uploaded in one go.");
        }
    }

    static String promptAdditions(String currentPrompt, String updatedPrompt) {
        java.util.Set<String> existingSentences = new java.util.HashSet<>();
        if (currentPrompt != null) {
            for (String sentence : currentPrompt.split("(?<=[.!?])\\s+")) {
                if (!sentence.isBlank()) {
                    existingSentences.add(sentence.trim());
                }
            }
        }
        if (updatedPrompt == null) {
            return "";
        }
        return java.util.Arrays.stream(updatedPrompt.split("(?<=[.!?])\\s+"))
                .map(String::trim)
                .filter(sentence -> !sentence.isEmpty() && !existingSentences.contains(sentence))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static String sanitiseLogValue(String value) {
        return value.replaceAll("[\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Loads the currently saved personality prompt.
     *
     * @return the saved prompt, or an empty string when none exists
     * @throws IOException if the prompt cannot be read
     */
    public synchronized String loadPrompt() throws IOException {
        return promptStore.load();
    }

    /**
     * Saves the user's personality prompt.
     *
     * @param prompt the replacement prompt
     * @throws IllegalArgumentException if the prompt exceeds the supported length
     * @throws IOException if the prompt cannot be saved
     */
    public synchronized void savePrompt(String prompt) throws IOException {
        promptStore.save(prompt);
    }

    /**
     * Removes the saved personality prompt without changing base system instructions.
     *
     * @throws IOException if the prompt cannot be removed
     */
    public synchronized void resetPrompt() throws IOException {
        promptStore.reset();
    }

    /**
     * Loads the user's saved keyword/tag preferences and opinion scores.
     *
     * @return saved interests in display order
     * @throws IOException if the memory file cannot be read
     */
    public synchronized List<InterestMemoryStore.ScoredInterest> loadScoredInterests() throws IOException {
        return memoryStore.loadScoredInterests();
    }

    /**
     * Adds a scored interest or updates the score for an existing keyword.
     *
     * @param keyword the user's keyword or tag
     * @param score an integer from 1 (strongly negative) to 5 (strongly positive)
     * @return the normalised saved interest
     * @throws IllegalArgumentException if the keyword or score is invalid
     * @throws IOException if the interest cannot be persisted
     */
    public synchronized InterestMemoryStore.ScoredInterest saveScoredInterest(String keyword, int score)
            throws IOException {
        return memoryStore.saveScoredInterest(keyword, score);
    }

    private static boolean isHtmlFilename(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".html") || lower.endsWith(".htm");
    }

    private static String displayFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "Unnamed HTML file";
        }
        return filename.replaceAll("[\\p{Cntrl}]", "").trim();
    }

    /**
     * Per-file processing error returned from an upload batch.
     *
     * @param filename the selected file's name
     * @param message the validation or processing failure
     */
    public record FileFailure(String filename, String message) {
    }

    /**
     * Aggregate result of an upload batch, including failures that did not prevent other files from processing.
     *
     * @param successfulFileCount number of files processed successfully
     * @param savedTagCount number of inferred tags saved across successful files
     * @param analyzedMediaCount number of media items analysed across successful files
     * @param failures per-file failures, including filenames
     * @param personalityPrompt the latest saved personality prompt
     */
    public record BatchSubmissionResult(
            int successfulFileCount,
            int savedTagCount,
            int analyzedMediaCount,
            List<FileFailure> failures,
            String personalityPrompt
    ) {
        /**
         * Creates an aggregate batch result with an immutable failure list.
         *
         * @param successfulFileCount number of files processed successfully
         * @param savedTagCount number of inferred tags persisted
         * @param analyzedMediaCount number of media items analysed
         * @param failures per-file failures
         * @param personalityPrompt the latest saved personality prompt
         */
        public BatchSubmissionResult {
            failures = List.copyOf(failures);
        }
    }

    /**
     * Result of analysing one document or image submission.
     *
     * @param savedTagCount number of inferred tags persisted
     * @param analyzedMediaCount number of media items analysed
     * @param personalityPrompt the resulting saved personality prompt
     */
    public record SubmissionResult(int savedTagCount, int analyzedMediaCount, String personalityPrompt) {
    }
}
