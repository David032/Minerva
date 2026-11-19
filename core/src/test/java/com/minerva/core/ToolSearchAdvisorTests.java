package com.minerva.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.advisors.AutoMemoryToolsAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("Unit")
class ToolSearchAdvisorTests {
    @Test
    void disabledSearchPreservesDirectToolCalling() {
        var calls = new AtomicInteger();
        ChatModel model = model(prompt -> {
            if (calls.getAndIncrement() == 0) {
                assertEquals(Set.of("weatherForecast"), toolNames(prompt));
                return toolCall("weatherForecast", "{}");
            }
            assertTrue(toolResults(prompt).contains("sunny"));
            return text("sunny");
        });

        assertEquals("sunny", client(model, false, new WeatherTools())
                .prompt().user("weather").call().content());
        assertEquals(2, calls.get());
    }

    @Test
    void searchDiscoversAndExecutesToolsAcrossMultipleSearches(@TempDir Path memories) {
        var calls = new AtomicInteger();
        ChatModel model = model(prompt -> switch (calls.getAndIncrement()) {
            case 0 -> {
                assertEquals(Set.of("toolSearchTool"), toolNames(prompt));
                yield toolCall("toolSearchTool", "{\"query\":\"weather forecast\"}");
            }
            case 1 -> {
                assertTrue(toolNames(prompt).contains("weatherForecast"));
                yield toolCall("weatherForecast", "{}");
            }
            case 2 -> {
                assertTrue(toolResults(prompt).contains("sunny"));
                yield toolCall("toolSearchTool", "{\"query\":\"clock time\"}");
            }
            case 3 -> {
                assertTrue(toolNames(prompt).containsAll(Set.of("weatherForecast", "clockTime")));
                yield toolCall("clockTime", "{}");
            }
            default -> {
                assertTrue(toolResults(prompt).contains("noon"));
                yield text("sunny at noon");
            }
        });
        var memory = MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(20).build()).build();
        var longTermMemory = AutoMemoryToolsAdvisor.builder().memoriesRootDirectory(memories.toString()).build();
        var advisors = ConversationManager.withToolSearch(new Advisor[]{memory, longTermMemory}, true);
        ChatClient client = ChatClient.builder(model).defaultAdvisors(advisors)
                .defaultSystem("You are an assistant.").defaultTools(new WeatherTools(), new ClockTools()).build();

        assertEquals("sunny at noon", request(client, "foreground"));
        assertEquals(5, calls.get());
    }

    @Test
    void noMatchingToolDoesNotExposeOrExecuteOtherTools() {
        var calls = new AtomicInteger();
        ChatModel model = model(prompt -> {
            if (calls.getAndIncrement() == 0) {
                return toolCall("toolSearchTool", "{\"query\":\"unfindablevolcanosubmarine\"}");
            }
            assertEquals(Set.of("toolSearchTool"), toolNames(prompt));
            assertTrue(toolResults(prompt).contains("[]"));
            return text("No matching tool");
        });

        assertEquals("No matching tool", request(client(model, true, new WeatherTools()), "foreground"));
        assertEquals(2, calls.get());
    }

    @Test
    void missingSessionIdFailsRatherThanSilentlyDisablingTools() {
        ChatClient client = client(model(prompt -> text("unexpected")), true, new WeatherTools());

        assertThrows(IllegalArgumentException.class, () -> client.prompt().user("weather").call().content());
    }

    @Test
    void searchConfigurationDoesNotAffectOtherClients() {
        ChatModel model = model(prompt -> {
            assertTrue(toolNames(prompt).isEmpty());
            return text("summary");
        });
        var builder = ChatClient.builder(model);
        builder.clone().defaultAdvisors(ConversationManager.withToolSearch(new Advisor[0], true)).build();
        ChatClient toolFreeClient = builder.clone().defaultSystem("Summarise").build();

        assertEquals("summary", toolFreeClient.prompt().user("Summarise this").call().content());
    }

    @Test
    void rebuiltClientReindexesToolsForEachSession() {
        var advisors = ConversationManager.withToolSearch(new Advisor[0], true);
        var seen = new ArrayList<Set<String>>();
        ChatModel model = model(prompt -> {
            if (toolResults(prompt).isEmpty()) {
                return toolCall("toolSearchTool", "{\"query\":\"clock time\"}");
            }
            seen.add(toolNames(prompt));
            return text("done");
        });
        ChatClient first = ChatClient.builder(model).defaultAdvisors(advisors)
                .defaultSystem("Assistant").defaultTools(new WeatherTools()).build();
        ChatClient rebuilt = ChatClient.builder(model).defaultAdvisors(advisors)
                .defaultSystem("Assistant").defaultTools(new ClockTools()).build();

        assertEquals("done", request(first, "foreground"));
        assertEquals("done", request(rebuilt, "foreground"));
        assertEquals("done", request(first, "heartbeat"));
        assertEquals(List.of(Set.of("toolSearchTool"), Set.of("toolSearchTool", "clockTime"),
                Set.of("toolSearchTool")), seen);
    }

    private static ChatClient client(ChatModel model, boolean searchEnabled, Object... tools) {
        return ChatClient.builder(model)
                .defaultAdvisors(ConversationManager.withToolSearch(new Advisor[0], searchEnabled))
                .defaultSystem("You are an assistant.")
                .defaultTools(tools)
                .build();
    }

    private static String request(ChatClient client, String sessionId) {
        return client.prompt().user("Help")
                .advisors(advisor -> advisor.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId))
                .call().content();
    }

    private static ChatModel model(java.util.function.Function<Prompt, ChatResponse> responses) {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> responses.apply(invocation.getArgument(0)));
        return model;
    }

    private static Set<String> toolNames(Prompt prompt) {
        var callbacks = ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks();
        if (callbacks == null) {
            return Set.of();
        }
        return callbacks.stream()
                .map(callback -> callback.getToolDefinition().name()).collect(Collectors.toSet());
    }

    private static String toolResults(Prompt prompt) {
        return prompt.getInstructions().stream().filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast).flatMap(message -> message.getResponses().stream())
                .map(ToolResponseMessage.ToolResponse::responseData).collect(Collectors.joining(" "));
    }

    private static ChatResponse toolCall(String name, String arguments) {
        var message = AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + name, "function", name, arguments))).build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static ChatResponse text(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    static class WeatherTools {
        @Tool(description = "Get the weather forecast")
        public String weatherForecast() {
            return "sunny";
        }
    }

    static class ClockTools {
        @Tool(description = "Get the clock time")
        public String clockTime() {
            return "noon";
        }
    }
}
