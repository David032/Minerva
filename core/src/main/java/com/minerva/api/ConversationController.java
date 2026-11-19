package com.minerva.api;

import com.minerva.core.ConversationManager;
import com.minerva.core.AiActivityGate;
import com.minerva.core.reminders.ReminderTools;
import com.minerva.core.journiv.JournivService;
import com.minerva.core.journiv.JournivTools;
import com.minerva.api.personality.MediaPersonalityService;
import com.minerva.tools.DateTimeTools;
import com.minerva.tools.RssTools;
import com.minerva.skills.SkillImportService;
import com.minerva.utils.FileManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.ai.session.SessionService;
import org.springframework.stereotype.Controller;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Controller
@ResponseBody
public class ConversationController {
    private final ConversationOperations conversation;
    private final FileManager fileManager;
    private static final int MAX_CHAT_IMAGE_BYTES = 5 * 1024 * 1024;

    @Autowired
    ConversationController(
            ChatClient.Builder ai,
            SessionService sessionService,
            SkillImportService skillImportService,
            DateTimeTools dateTimeTools,
            ReminderTools reminderTools,
            RssTools rssTools,
            MediaPersonalityService personalityService,
            AiActivityGate activityGate,
            JournivService journivService,
            JournivTools journivTools,
            @Value("${minerva.tool-search.enabled:false}") boolean toolSearchEnabled
    ) throws IOException {
        ConversationManager.Initialize(
                ai, sessionService, skillImportService, dateTimeTools, reminderTools,
                rssTools, personalityService, activityGate, journivService, journivTools, toolSearchEnabled
        );
        fileManager = FileManager.getInstance();
        conversation = new ConversationOperations() {
            @Override
            public String sendMessage(String message) {
                return ConversationManager.sendMessage(message);
            }

            @Override
            public String seekImageOpinion(String pathToFile, String message, MimeType imageMimeType) throws IOException {
                return ConversationManager.seekImageOpinion(pathToFile, message, imageMimeType);
            }

            @Override
            public File audioProcessing(String pathToFile) throws IOException {
                return ConversationManager.audioProcessing(pathToFile);
            }
        };
    }

    ConversationController(ConversationOperations conversation, FileManager fileManager) {
        this.conversation = conversation;
        this.fileManager = fileManager;
    }

    @PostMapping("/message")
    String message(@RequestParam String message) {
        return conversation.sendMessage(message);
    }

    /**
     * Analyses a supported image and optional message from the web-chat composer.
     *
     * @param message optional question or context for the image
     * @param image image attachment, limited to 5 MB
     * @return the model's response
     * @throws IOException if the image cannot be staged or analysed
     */
    @PostMapping("/image")
    String image(
            @RequestParam(required = false) String message,
            @RequestParam MultipartFile image
    ) throws IOException {
        if (image.getSize() > MAX_CHAT_IMAGE_BYTES) {
            throw new IllegalArgumentException("Chat images must be 5 MB or smaller.");
        }
        byte[] imageBytes = image.getBytes();
        if (imageBytes.length == 0) {
            throw new IllegalArgumentException("Choose a non-empty image.");
        }
        if (imageBytes.length > MAX_CHAT_IMAGE_BYTES) {
            throw new IllegalArgumentException("Chat images must be 5 MB or smaller.");
        }
        MimeType imageMimeType = detectImageMimeType(imageBytes);
        String uploadName = UUID.randomUUID() + imageExtension(imageMimeType);
        var uploadPath = fileManager.UploadFile(uploadName, image);
        try {
            return conversation.seekImageOpinion(uploadPath.toString(), message, imageMimeType);
        } finally {
            fileManager.DeleteFile(uploadName);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String invalidChatRequest(IllegalArgumentException exception) {
        return exception.getMessage();
    }

    private static MimeType detectImageMimeType(byte[] content) {
        if (content.length >= 3 && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
        if (content.length >= 8 && (content[0] & 0xff) == 0x89
                && content[1] == 'P' && content[2] == 'N' && content[3] == 'G'
                && content[4] == 13 && content[5] == 10 && content[6] == 26 && content[7] == 10) {
            return MimeTypeUtils.parseMimeType("image/png");
        }
        if (content.length >= 6) {
            String signature = new String(content, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
            if (signature.equals("GIF87a") || signature.equals("GIF89a")) {
                return MimeTypeUtils.parseMimeType("image/gif");
            }
        }
        if (content.length >= 12
                && new String(content, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(content, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) {
            return MimeTypeUtils.parseMimeType("image/webp");
        }
        throw new IllegalArgumentException("Choose a supported JPEG, PNG, GIF, or WebP image.");
    }

    private static String imageExtension(MimeType mimeType) {
        return switch (mimeType.getSubtype()) {
            case "jpeg" -> ".jpg";
            case "png" -> ".png";
            case "gif" -> ".gif";
            case "webp" -> ".webp";
            default -> throw new IllegalArgumentException("Choose a supported image format.");
        };
    }

    @PostMapping("/audio")
    File audio(@RequestParam MultipartFile audio) throws IOException {
        var uploadPath = fileManager.UploadFile(audio.getOriginalFilename(),audio);
        return conversation.audioProcessing(uploadPath.toString());
    }

    interface ConversationOperations {
        String sendMessage(String message);

        String seekImageOpinion(String pathToFile, String message, MimeType imageMimeType) throws IOException;

        File audioProcessing(String pathToFile) throws IOException;
    }

}
