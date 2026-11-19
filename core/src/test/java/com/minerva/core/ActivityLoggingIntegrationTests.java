package com.minerva.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.tools.AutoMemoryTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("ContinuousIntegration")
@ExtendWith(OutputCaptureExtension.class)
class ActivityLoggingIntegrationTests {
    @TempDir
    Path directory;

    @Test
    void springToolManagerPropagatesHeartbeatContextToMemoryAndSkillCallbacks(CapturedOutput output) throws Exception {
        var tools = ConversationManager.loggingCallbacks(new Object[]{
                AutoMemoryTools.builder().memoriesDir(directory.toString()).build(),
                SkillsTool.builder().addSkillsResource(new ClassPathResource("skills")).build()
        });
        var run = new HeartbeatRun();
        var options = ToolCallingChatOptions.builder().toolCallbacks(tools)
                .toolContext(Map.of(HeartbeatRun.CONTEXT_KEY, run)).build();
        var prompt = new Prompt("Create a memory", options);
        var message = AssistantMessage.builder().toolCalls(List.of(
                new AssistantMessage.ToolCall("1", "function", "MemoryCreate",
                        "{\"path\":\"test.md\",\"fileText\":\"private\"}"),
                new AssistantMessage.ToolCall("2", "function", "Skill",
                        "{\"command\":\"nonexistent-skill\"}"))).build();
        var response = new ChatResponse(List.of(new Generation(message)));

        ToolCallingManager.builder().build().executeToolCalls(prompt, response);

        assertEquals("private", Files.readString(directory.resolve("test.md")));
        assertEquals(2, run.actionCount());
        assertTrue(output.getOut().contains("Memory added via=MemoryCreate heartbeat=" + run.id()));
        assertTrue(output.getOut().contains("tool=Skill outcome=returned"));
    }
}
