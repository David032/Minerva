package com.minerva.api.personality;

import com.minerva.core.personality.PersonalityImportJob;
import com.minerva.core.personality.PersonalityImportJobRepository;
import com.minerva.core.personality.PersonalityImportJobStatus;
import com.minerva.core.personality.PersonalityImportKind;
import com.minerva.utils.Directory;
import com.minerva.utils.FileManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class PersonalityImportJobServiceTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void stagesUploadsAndReturnsBeforeBackgroundProcessing() throws Exception {
        Path uploads = temporaryDirectory.resolve("uploads");
        var repository = mock(PersonalityImportJobRepository.class);
        var personalityService = mock(MediaPersonalityService.class);
        var fileManager = mock(FileManager.class);
        when(fileManager.getDirectoryPath(Directory.UPLOADS)).thenReturn(uploads.toString());
        when(repository.save(any(PersonalityImportJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        List<Runnable> tasks = new java.util.ArrayList<>();
        TaskExecutor executor = tasks::add;
        var service = new PersonalityImportJobService(repository, personalityService, fileManager, executor);

        var result = service.submit(PersonalityImportKind.IMAGE_BATCH,
                List.of(new MockMultipartFile("imageFiles", "garden.png", "image/png", new byte[] {1, 2, 3})),
                "I like this.");

        assertEquals(PersonalityImportJobStatus.QUEUED, result.status());
        assertEquals(1, tasks.size());
        var job = org.mockito.ArgumentCaptor.forClass(PersonalityImportJob.class);
        org.mockito.Mockito.verify(repository).save(job.capture());
        Path stagedFile = uploads.resolve("personality-imports").resolve(job.getValue().getId().toString())
                .resolve("00000.upload");
        assertTrue(Files.isRegularFile(stagedFile));
        assertEquals(3, Files.size(stagedFile));
        assertEquals("I like this.", job.getValue().getGuidance());
    }

    @Test
    void requeuesInterruptedJobsOnApplicationRecovery() {
        var repository = mock(PersonalityImportJobRepository.class);
        var personalityService = mock(MediaPersonalityService.class);
        var fileManager = mock(FileManager.class);
        List<Runnable> tasks = new java.util.ArrayList<>();
        TaskExecutor executor = tasks::add;
        var service = new PersonalityImportJobService(repository, personalityService, fileManager, executor);
        PersonalityImportJob job = new PersonalityImportJob(UUID.randomUUID(), PersonalityImportKind.ZIP,
                "", UUID.randomUUID().toString(), "[]", Instant.now());
        job.start(Instant.now());
        when(repository.findAllByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(job));
        when(repository.save(any(PersonalityImportJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.recoverJobs();

        assertEquals(PersonalityImportJobStatus.QUEUED, job.getStatus());
        assertEquals(1, tasks.size());
    }

    @Test
    void storesTheBackgroundResultAndRemovesStagedFiles() throws Exception {
        Path uploads = temporaryDirectory.resolve("uploads");
        var repository = mock(PersonalityImportJobRepository.class);
        var personalityService = mock(MediaPersonalityService.class);
        var fileManager = mock(FileManager.class);
        when(fileManager.getDirectoryPath(Directory.UPLOADS)).thenReturn(uploads.toString());
        Map<UUID, PersonalityImportJob> jobs = new java.util.HashMap<>();
        when(repository.save(any(PersonalityImportJob.class))).thenAnswer(invocation -> {
            PersonalityImportJob job = invocation.getArgument(0);
            jobs.put(job.getId(), job);
            return job;
        });
        when(repository.findById(any())).thenAnswer(invocation ->
                java.util.Optional.ofNullable(jobs.get(invocation.getArgument(0))));
        when(personalityService.processImageBatch(anyList(), anyString())).thenReturn(
                new MediaPersonalityService.BatchSubmissionResult(1, 2, 3,
                        List.of(new MediaPersonalityService.FileFailure("broken.png", "Invalid image.")),
                        "I enjoy botanical art."));
        List<Runnable> tasks = new java.util.ArrayList<>();
        TaskExecutor executor = tasks::add;
        var service = new PersonalityImportJobService(repository, personalityService, fileManager, executor);
        var submitted = service.submit(PersonalityImportKind.IMAGE_BATCH,
                List.of(new MockMultipartFile("imageFiles", "garden.png", "image/png", new byte[] {1})), "");
        Path stagedDirectory = uploads.resolve("personality-imports").resolve(submitted.id().toString());

        tasks.getFirst().run();

        var completed = service.get(submitted.id());
        assertEquals(PersonalityImportJobStatus.COMPLETED_WITH_ERRORS, completed.status());
        assertEquals(1, completed.successfulFileCount());
        assertEquals("broken.png", completed.failures().getFirst().filename());
        assertFalse(Files.exists(stagedDirectory));
        verify(personalityService).processImageBatch(anyList(), anyString());
    }
}
