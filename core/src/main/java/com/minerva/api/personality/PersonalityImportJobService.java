package com.minerva.api.personality;

import com.google.gson.Gson;
import com.minerva.core.personality.PersonalityImportJob;
import com.minerva.core.personality.PersonalityImportJobRepository;
import com.minerva.core.personality.PersonalityImportJobStatus;
import com.minerva.core.personality.PersonalityImportKind;
import com.minerva.utils.Directory;
import com.minerva.utils.FileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Persists uploaded personality imports and runs them outside the submitting HTTP request.
 */
@Service
public class PersonalityImportJobService {
    private static final Logger logger = LoggerFactory.getLogger(PersonalityImportJobService.class);
    private static final int MAX_BATCH_FILES = 50;
    private static final int MAX_GUIDANCE_LENGTH = 2_000;
    private static final Gson GSON = new Gson();

    private final PersonalityImportJobRepository repository;
    private final MediaPersonalityService personalityService;
    private final FileManager fileManager;
    private final TaskExecutor executor;
    private final ConcurrentMap<UUID, Boolean> scheduledJobs = new ConcurrentHashMap<>();

    /**
     * Creates the durable import-job service.
     *
     * @param repository persisted job lifecycle and results
     * @param personalityService existing batch and ZIP processing workflows
     * @param fileManager Minerva's persistent uploads directory
     * @param executor single-worker background executor
     */
    public PersonalityImportJobService(
            PersonalityImportJobRepository repository,
            MediaPersonalityService personalityService,
            FileManager fileManager,
            @Qualifier("personalityImportExecutor") TaskExecutor executor
    ) {
        this.repository = repository;
        this.personalityService = personalityService;
        this.fileManager = fileManager;
        this.executor = executor;
    }

    /**
     * Stages an upload and returns immediately with its queued job status.
     *
     * @param kind batch type
     * @param files submitted files
     * @param guidance optional preference guidance
     * @return the newly queued job
     * @throws IllegalArgumentException if the upload does not match the selected type or exceeds its limits
     * @throws IOException if the upload cannot be staged
     */
    public PersonalityImportJobResponse submit(
            PersonalityImportKind kind,
            List<MultipartFile> files,
            String guidance
    ) throws IOException {
        validate(kind, files, guidance);
        UUID id = UUID.randomUUID();
        Path uploadDirectory = uploadRoot().resolve(id.toString());
        Files.createDirectories(uploadDirectory);
        List<StagedFile> manifest = new ArrayList<>();
        boolean persisted = false;
        try {
            int index = 0;
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String storedName = String.format(java.util.Locale.ROOT, "%05d.upload", index++);
                Path storedPath = uploadDirectory.resolve(storedName);
                try (var input = file.getInputStream()) {
                    Files.copy(input, storedPath, StandardCopyOption.REPLACE_EXISTING);
                }
                String originalFilename = displayFilename(file.getOriginalFilename());
                String contentType = file.getContentType();
                if (contentType == null || contentType.isBlank()) {
                    contentType = URLConnection.guessContentTypeFromName(originalFilename);
                }
                manifest.add(new StagedFile(storedName, originalFilename, contentType, Files.size(storedPath)));
            }
            if (manifest.isEmpty()) {
                throw new IllegalArgumentException("Choose at least one non-empty file to import.");
            }
            Instant now = Instant.now();
            PersonalityImportJob job = new PersonalityImportJob(
                    id, kind, guidance == null ? "" : guidance.trim(), id.toString(), GSON.toJson(manifest), now);
            repository.save(job);
            persisted = true;
            schedule(id);
            return response(job, List.of());
        } catch (IOException | RuntimeException e) {
            if (!persisted) {
                deleteUploadDirectory(uploadDirectory);
            }
            throw e;
        }
    }

    /**
     * Returns the current status and result for an import.
     *
     * @param id job identifier
     * @return persisted job response
     * @throws NoSuchElementException if the identifier is unknown
     */
    public PersonalityImportJobResponse get(UUID id) {
        PersonalityImportJob job = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Personality import job not found: " + id));
        return response(job, failures(job));
    }

    /**
     * Requeues unfinished imports when the application starts.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverJobs() {
        List<PersonalityImportJob> jobs = repository.findAllByStatusInOrderByCreatedAtAsc(
                List.of(PersonalityImportJobStatus.QUEUED, PersonalityImportJobStatus.RUNNING));
        for (PersonalityImportJob job : jobs) {
            job.requeue(Instant.now());
            repository.save(job);
            schedule(job.getId());
        }
    }

    /**
     * Redispatches queued jobs if the executor was temporarily unavailable.
     */
    @Scheduled(fixedDelayString = "${minerva.personality.import-poll-interval:1000}")
    public void dispatchQueuedJobs() {
        List<PersonalityImportJob> jobs = repository.findAllByStatusInOrderByCreatedAtAsc(
                List.of(PersonalityImportJobStatus.QUEUED));
        jobs.forEach(job -> schedule(job.getId()));
    }

    private void schedule(UUID id) {
        if (scheduledJobs.putIfAbsent(id, Boolean.TRUE) != null) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    process(id);
                } finally {
                    scheduledJobs.remove(id);
                }
            });
        } catch (TaskRejectedException e) {
            scheduledJobs.remove(id);
            logger.warn("Personality import {} remains queued because the background executor rejected it.", id, e);
        }
    }

    private void process(UUID id) {
        PersonalityImportJob job = repository.findById(id).orElse(null);
        if (job == null || job.getStatus() != PersonalityImportJobStatus.QUEUED) {
            return;
        }
        job.start(Instant.now());
        job = repository.save(job);
        MediaPersonalityService.BatchSubmissionResult result;
        try {
            List<MultipartFile> files = stagedFiles(job);
            result = switch (job.getKind()) {
                case HTML_BATCH -> personalityService.processHtmlBatch(files, job.getGuidance());
                case IMAGE_BATCH -> personalityService.processImageBatch(files, job.getGuidance());
                case ZIP -> personalityService.processZip(files.getFirst(), job.getGuidance());
            };
        } catch (IOException | RuntimeException e) {
            logger.error("Personality import {} failed.", id, e);
            job.complete(PersonalityImportJobStatus.FAILED, 0, 0, 0, "[]",
                    safeErrorMessage(e), Instant.now());
            repository.save(job);
            deleteUploadDirectory(uploadRoot().resolve(id.toString()));
            return;
        }
        PersonalityImportJobStatus status = result.successfulFileCount() == 0
                ? PersonalityImportJobStatus.FAILED
                : result.failures().isEmpty()
                ? PersonalityImportJobStatus.COMPLETED
                : PersonalityImportJobStatus.COMPLETED_WITH_ERRORS;
        String error = status == PersonalityImportJobStatus.FAILED
                ? "No files in this import were processed successfully."
                : null;
        job.complete(status, result.successfulFileCount(), result.savedTagCount(), result.analyzedMediaCount(),
                GSON.toJson(result.failures()), error, Instant.now());
        repository.save(job);
        deleteUploadDirectory(uploadRoot().resolve(id.toString()));
    }

    private List<MultipartFile> stagedFiles(PersonalityImportJob job) throws IOException {
        Path root = uploadRoot().toAbsolutePath().normalize();
        Path directory = root.resolve(job.getUploadDirectory()).normalize();
        if (!root.equals(directory.getParent())
                || !job.getId().toString().equals(directory.getFileName().toString())) {
            throw new IOException("The stored personality import path is invalid.");
        }
        List<StagedFile> manifest;
        try {
            StagedFile[] parsed = GSON.fromJson(job.getFileManifest(), StagedFile[].class);
            manifest = parsed == null ? List.of() : List.of(parsed);
        } catch (RuntimeException e) {
            throw new IOException("The stored personality import manifest is invalid.", e);
        }
        List<MultipartFile> files = new ArrayList<>();
        for (StagedFile file : manifest) {
            Path path = directory.resolve(file.storedName()).normalize();
            if (!path.startsWith(directory) || !Files.isRegularFile(path)) {
                throw new IOException("A staged personality import file is missing.");
            }
            files.add(new StagedMultipartFile(file, path));
        }
        if (files.isEmpty()) {
            throw new IOException("The personality import has no staged files.");
        }
        return List.copyOf(files);
    }

    private PersonalityImportJobResponse response(PersonalityImportJob job, List<MediaPersonalityService.FileFailure> failures) {
        return new PersonalityImportJobResponse(job.getId(), job.getKind(), job.getStatus(), job.getCreatedAt(),
                job.getUpdatedAt(), job.getSuccessfulFileCount(), job.getSavedTagCount(),
                job.getAnalysedMediaCount(), failures, job.getErrorMessage());
    }

    private static List<MediaPersonalityService.FileFailure> failures(PersonalityImportJob job) {
        try {
            MediaPersonalityService.FileFailure[] parsed = GSON.fromJson(
                    job.getFailuresJson(), MediaPersonalityService.FileFailure[].class);
            return parsed == null ? List.of() : List.of(parsed);
        } catch (RuntimeException e) {
            throw new IllegalStateException("The saved personality import result is invalid.", e);
        }
    }

    private void validate(PersonalityImportKind kind, List<MultipartFile> files, String guidance) {
        if (kind == null) {
            throw new IllegalArgumentException("Choose an import type.");
        }
        if (guidance != null && guidance.length() > MAX_GUIDANCE_LENGTH) {
            throw new IllegalArgumentException("Guidance must be 2,000 characters or fewer.");
        }
        long nonEmptyFiles = files == null ? 0 : files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .count();
        if (nonEmptyFiles == 0) {
            throw new IllegalArgumentException("Choose at least one non-empty file to import.");
        }
        if (kind == PersonalityImportKind.ZIP && nonEmptyFiles != 1) {
            throw new IllegalArgumentException("Submit exactly one ZIP archive.");
        }
        if (kind != PersonalityImportKind.ZIP && nonEmptyFiles > MAX_BATCH_FILES) {
            throw new IllegalArgumentException("A maximum of 50 files can be uploaded in one go.");
        }
    }

    private Path uploadRoot() {
        return Path.of(fileManager.getDirectoryPath(Directory.UPLOADS)).resolve("personality-imports");
    }

    private static String displayFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "Unnamed file";
        }
        String display = filename.replaceAll("[\\p{Cntrl}]", "").trim();
        return display.length() > 255 ? display.substring(0, 255) : display;
    }

    private static String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Personality import processing failed.";
        }
        return message.length() > 2_000 ? message.substring(0, 2_000) : message;
    }

    private static void deleteUploadDirectory(Path directory) {
        if (Files.notExists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            logger.error("Could not remove staged personality import files from {}.", directory, e);
        }
    }

    private record StagedFile(String storedName, String originalFilename, String contentType, long size) {
    }

    private record StagedMultipartFile(StagedFile file, Path path) implements MultipartFile {
        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return file.originalFilename();
        }

        @Override
        public String getContentType() {
            return file.contentType();
        }

        @Override
        public boolean isEmpty() {
            return file.size() == 0;
        }

        @Override
        public long getSize() {
            return file.size();
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(File destination) throws IOException {
            Files.copy(path, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
