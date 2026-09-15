package com.minerva.minervacore;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.session.SessionService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@ResponseBody
public class ConversationController {


    ConversationController (ChatClient.Builder ai, SessionService sessionService) {
        ConversationManager.Initialize(ai, sessionService);
    }

    @GetMapping("/message")
    String message(@RequestParam String message) {
        return ConversationManager.sendMessage(message);
    }
}
