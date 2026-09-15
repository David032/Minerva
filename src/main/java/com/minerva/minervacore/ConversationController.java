package com.minerva.minervacore;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.compaction.SlidingWindowCompactionStrategy;
import org.springframework.ai.session.compaction.TurnCountTrigger;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@ResponseBody
public class ConversationController {

    private final ChatClient ai;

    ConversationController (ChatClient.Builder ai, SessionService sessionService) {
        SessionMemoryAdvisor advisor = SessionMemoryAdvisor.builder(sessionService).build();
        this.ai = ai.defaultAdvisors(advisor).build();
    }

    @GetMapping("/message")
    String message(@RequestParam String message) {
        return ai
                .prompt()
                .user(message)
                .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "session-abc"))
                .call()
                .content();
    }
}
