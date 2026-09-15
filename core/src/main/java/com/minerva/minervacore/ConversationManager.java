package com.minerva.minervacore;

import com.minerva.minervatools.DateTimeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.compaction.SlidingWindowCompactionStrategy;
import org.springframework.ai.session.compaction.TurnCountTrigger;

public class ConversationManager {
    private static ConversationManager single_instance = null;
    private static ChatClient aiChatClient;

    private ConversationManager()
    {
        single_instance = this;
    }

    public static void Initialize(ChatClient.Builder ai, SessionService sessionService)
    {
        SessionMemoryAdvisor advisor = SessionMemoryAdvisor.builder(sessionService)
                .defaultUserId("user")
                // Compact when 20 turns accumulate, keeping the last 10 events
                .compactionTrigger(new TurnCountTrigger(20))
                .compactionStrategy(SlidingWindowCompactionStrategy.builder().maxEvents(10).build())
                .build();
        aiChatClient = ai.defaultAdvisors(advisor).build();
    }

    public static synchronized ConversationManager getInstance()
    {
        if (single_instance == null)
            single_instance = new ConversationManager();

        return single_instance;
    }

    public static String sendMessage(String message) {
        return aiChatClient
                .prompt()
                .tools(new DateTimeTools())
                .user(message)
                .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "session-abc"))
                .call()
                .content();
    }
}
