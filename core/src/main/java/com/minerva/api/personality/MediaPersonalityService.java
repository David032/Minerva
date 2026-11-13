package com.minerva.api.personality;

import com.minerva.core.personality.PersonalityPromptStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class MediaPersonalityService {
    private final MediaDocumentExtractor extractor;
    private final RemoteMediaFetcher mediaFetcher;
    private final MediaPreprocessor mediaPreprocessor;
    private final PersonalityAnalyzer analyzer;
    private final PersonalityPromptStore promptStore;
    private final InterestMemoryStore memoryStore;

    public MediaPersonalityService(
            MediaDocumentExtractor extractor,
            RemoteMediaFetcher mediaFetcher,
            MediaPreprocessor mediaPreprocessor,
            PersonalityAnalyzer analyzer,
            PersonalityPromptStore promptStore,
            InterestMemoryStore memoryStore
    ) {
        this.extractor = extractor;
        this.mediaFetcher = mediaFetcher;
        this.mediaPreprocessor = mediaPreprocessor;
        this.analyzer = analyzer;
        this.promptStore = promptStore;
        this.memoryStore = memoryStore;
    }

    public synchronized SubmissionResult process(MultipartFile file, String guidance) throws IOException {
        MediaDocumentExtractor.Extraction extraction = extractor.extract(file);
        List<RemoteMediaFetcher.MediaAsset> fetchedMedia = mediaFetcher.fetch(extraction.mediaUrls());
        List<RemoteMediaFetcher.MediaAsset> media = mediaPreprocessor.prepare(fetchedMedia);
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
        return new SubmissionResult(extraction.tags().size(), extraction.mediaUrls().size(), updatedPrompt);
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
