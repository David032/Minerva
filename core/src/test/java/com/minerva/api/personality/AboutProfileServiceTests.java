package com.minerva.api.personality;

import com.minerva.api.library.MediaLibraryItem;
import com.minerva.api.library.MediaLibraryPage;
import com.minerva.api.library.MediaLibraryStore;
import com.minerva.api.personality.InterestMemoryStore;
import com.minerva.core.AiActivityGate;
import com.minerva.core.HeartbeatMemoryStore;
import com.minerva.core.personality.CustomizationStore;
import com.minerva.core.personality.AboutProfileService;
import com.minerva.core.personality.PersonalityPromptStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class AboutProfileServiceTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesBiographyFromPersonalityInterestsHeartbeatAndLikedMediaOncePerSourceSnapshot()
            throws IOException {
        var builder = mock(ChatClient.Builder.class);
        var chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("I enjoy gardens, careful research, and botanical art.", "Second biography");

        var personality = new PersonalityPromptStore(temporaryDirectory.resolve("personality.md"));
        personality.save("I appreciate thoughtful gardens.");
        var interests = new InterestMemoryStore(temporaryDirectory.resolve("memories.md"));
        interests.saveScoredInterest("Botanical art", 5);
        var activity = new HeartbeatMemoryStore(temporaryDirectory.resolve("heartbeat-memories.json"));
        activity.append(Instant.parse("2026-10-01T10:00:00Z"), "SUCCEEDED", 2, "Reviewed a garden article.");
        var media = mock(MediaLibraryStore.class);
        when(media.page(10, null)).thenReturn(new MediaLibraryPage(List.of(new MediaLibraryItem(
                "media-id", "Botanical print", null, "image/png", "print.png",
                Instant.parse("2026-10-01T09:00:00Z"), "Matches Minerva's visual interests.")), null));
        var store = new CustomizationStore(temporaryDirectory.resolve("customization.json"));
        var service = new AboutProfileService(builder, new AiActivityGate(), store, personality,
                interests, activity, media);

        var first = service.loadPage();
        var second = service.loadPage();

        assertEquals("I enjoy gardens, careful research, and botanical art.", first.biography());
        assertEquals("Botanical art", first.interests().getFirst().keyword());
        assertEquals("Reviewed a garden article.", first.recentActivity().getFirst().outcome());
        assertEquals("Botanical print", first.likedMedia().getFirst().title());
        assertEquals(first.biography(), second.biography());
        verify(media, org.mockito.Mockito.times(2)).page(10, null);

        activity.append(Instant.parse("2026-10-02T10:00:00Z"), "SUCCEEDED", 1, "Saved a new local activity.");
        var refreshed = service.loadPage();
        assertEquals("Second biography", refreshed.biography());
    }

    @Test
    void defersBiographyRefreshWhileAnotherAiTaskIsActive() throws IOException {
        var builder = mock(ChatClient.Builder.class);
        var chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("The refreshed biography.");
        var personality = new PersonalityPromptStore(temporaryDirectory.resolve("personality.md"));
        personality.save("I like gardens.");
        var interests = new InterestMemoryStore(temporaryDirectory.resolve("memories.md"));
        var activity = new HeartbeatMemoryStore(temporaryDirectory.resolve("heartbeat-memories.json"));
        var media = mock(MediaLibraryStore.class);
        when(media.page(10, null)).thenReturn(new MediaLibraryPage(List.of(), null));
        AiActivityGate gate = new AiActivityGate();
        var store = new CustomizationStore(temporaryDirectory.resolve("customization.json"));
        store.saveBiography("The previous biography.", "old-source-digest");
        var service = new AboutProfileService(builder, gate, store, personality, interests, activity, media);

        assertTrue(gate.tryRunHeartbeat(() -> {
            var page = CompletableFuture.supplyAsync(() -> {
                try {
                    return service.loadPage();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).join();
            assertTrue(page.refreshPending());
            assertEquals("The previous biography.", page.biography());
            assertEquals("old-source-digest", page.biographySourceDigest());
            assertNotEquals(page.sourceDigest(), page.biographySourceDigest());
        }));

        var refreshed = service.loadPage();
        assertFalse(refreshed.refreshPending());
        assertEquals("The refreshed biography.", refreshed.biography());
        assertEquals(refreshed.sourceDigest(), refreshed.biographySourceDigest());
    }
}
