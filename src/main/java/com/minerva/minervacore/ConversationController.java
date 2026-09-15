package com.minerva.minervacore;

import com.minerva.minervatools.DateTimeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
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
        SessionMemoryAdvisor advisor = SessionMemoryAdvisor.builder(sessionService)
                .defaultUserId("user")
                // Compact when 20 turns accumulate, keeping the last 10 events
                .compactionTrigger(new TurnCountTrigger(20))
                .compactionStrategy(SlidingWindowCompactionStrategy.builder().maxEvents(10).build())
                .build();
        this.ai = ai.defaultAdvisors(advisor).build();
    }

    @GetMapping("/message")
    String message(@RequestParam String message) {
        return ai
                .prompt()
                .tools(new DateTimeTools())
                .user(message)
                .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "session-abc"))
                .call()
                .content();
    }
}
