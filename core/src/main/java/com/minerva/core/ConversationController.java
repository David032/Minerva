package com.minerva.core;

import com.minerva.utils.FileManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.session.SessionService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Controller
@ResponseBody
public class ConversationController {
    private final ConversationOperations conversation;
    private final FileManager fileManager;

    @Autowired
    ConversationController (ChatClient.Builder ai, SessionService sessionService) throws IOException {
        ConversationManager.Initialize(ai, sessionService);
        fileManager = FileManager.getInstance();
        conversation = new ConversationOperations() {
            @Override
            public String sendMessage(String message) {
                return ConversationManager.sendMessage(message);
            }

            @Override
            public String seekImageOpinion(String pathToFile, String message) throws IOException {
                return ConversationManager.seekImageOpinion(pathToFile, message);
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

    @PostMapping("/image")
    String image(@RequestParam(required = false) String message, @RequestParam MultipartFile image) throws IOException {
        var uploadPath = fileManager.UploadFile(image.getOriginalFilename(), image);
        try {
            return conversation.seekImageOpinion(uploadPath.toString(), message);
        } finally {
            fileManager.DeleteFile(image.getOriginalFilename());
        }
    }

    @PostMapping("/audio")
    File audio(@RequestParam MultipartFile audio) throws IOException {
        var uploadPath = fileManager.UploadFile(audio.getOriginalFilename(),audio);
        return conversation.audioProcessing(uploadPath.toString());
    }

    interface ConversationOperations {
        String sendMessage(String message);

        String seekImageOpinion(String pathToFile, String message) throws IOException;

        File audioProcessing(String pathToFile) throws IOException;
    }

}
