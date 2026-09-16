package com.minerva.core;

import com.minerva.utils.FileManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.session.SessionService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@ResponseBody
public class ConversationController {

    ConversationController (ChatClient.Builder ai, SessionService sessionService) {
        ConversationManager.Initialize(ai, sessionService);
    }

    @PostMapping("/message")
    String message(@RequestParam String message) {
        return ConversationManager.sendMessage(message);
    }

    @PostMapping("/image")
    String image(@RequestParam(required = false) String message, @RequestParam MultipartFile image) throws IOException {
        var uploadPath = FileManager.getInstance().UploadFile(image.getOriginalFilename(),image);
        String response = ConversationManager.seekImageOpinion(uploadPath.toString(),message);
        FileManager.getInstance().DeleteFile(image.getOriginalFilename());
        return response;
    }
}
