package com.minerva.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.tools.AutoMemoryTools;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("Unit")
@ExtendWith(OutputCaptureExtension.class)
class ActivityLoggingToolCallbackTests {
    @TempDir
    Path directory;

    @Test
    void recordsRealMemoryMutationsAndHeartbeatActionsWithoutContent(CapturedOutput output) {
        var tools = ToolCallbacks.from(AutoMemoryTools.builder().memoriesDir(directory.toString()).build());
        HeartbeatRun run = new HeartbeatRun();
        ToolContext context = new ToolContext(Map.of(HeartbeatRun.CONTEXT_KEY, run));
        ToolCallback create = wrap(tools, "MemoryCreate");
        ToolCallback replace = wrap(tools, "MemoryStrReplace");
        ToolCallback insert = wrap(tools, "MemoryInsert");
        ToolCallback rename = wrap(tools, "MemoryRename");
        ToolCallback delete = wrap(tools, "MemoryDelete");

        assertTrue(create.call("{\"path\":\"private.md\",\"fileText\":\"private secret\"}", context).contains("Successfully"));
        assertTrue(create.call("{\"path\":\"private.md\",\"fileText\":\"private secret\"}", context).contains("Error"));
        replace.call("{\"path\":\"private.md\",\"oldStr\":\"secret\",\"newStr\":\"secret\"}", context);
        replace.call("{\"path\":\"private.md\",\"oldStr\":\"secret\",\"newStr\":\"new secret\"}", context);
        insert.call("{\"path\":\"private.md\",\"insertLine\":1,\"insertText\":\"more private data\"}", context);
        rename.call("{\"oldPath\":\"private.md\",\"newPath\":\"renamed.md\"}", context);
        delete.call("{\"path\":\"renamed.md\"}", context);

        String logs = output.getOut();
        assertEquals(7, run.actionCount());
        assertTrue(logs.contains("Memory added via=MemoryCreate heartbeat=" + run.id()));
        assertEquals(1, count(logs, "Memory updated via=MemoryStrReplace"));
        assertEquals(1, count(logs, "Memory updated via=MemoryInsert"));
        assertTrue(logs.contains("Memory renamed via=MemoryRename"));
        assertTrue(logs.contains("Memory deleted via=MemoryDelete"));
        assertTrue(logs.contains("tool=MemoryCreate outcome=error"));
        assertFalse(logs.contains("private secret"));
        assertFalse(logs.contains("private.md"));
    }

    @Test
    void preservesCallbackMetadataContextAndFailure(CapturedOutput output) {
        ToolCallback delegate = mock(ToolCallback.class);
        var definition = org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("example").description("example").inputSchema("{}").build();
        when(delegate.getToolDefinition()).thenReturn(definition);
        var metadata = delegate.getToolMetadata();
        HeartbeatRun run = new HeartbeatRun();
        ToolContext context = new ToolContext(Map.of(HeartbeatRun.CONTEXT_KEY, run));
        when(delegate.call("input", context)).thenThrow(new IllegalArgumentException("secret"));
        var wrapped = new ActivityLoggingToolCallback(delegate);

        assertSame(definition, wrapped.getToolDefinition());
        assertSame(metadata, wrapped.getToolMetadata());
        assertThrows(IllegalArgumentException.class, () -> wrapped.call("input", context));
        assertEquals(1, run.actionCount());
        assertTrue(output.getOut().contains("outcome=exception type=IllegalArgumentException"));
        assertFalse(output.getOut().contains("secret"));
    }

    @Test
    void boundsAndFlattensHeartbeatResults(CapturedOutput output) {
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("example").description("example").inputSchema("{}").build());
        HeartbeatRun run = new HeartbeatRun();
        ToolContext context = new ToolContext(Map.of(HeartbeatRun.CONTEXT_KEY, run));
        String result = "result\n" + "x".repeat(600);
        when(delegate.call("input", context)).thenReturn(result);

        assertEquals(result, new ActivityLoggingToolCallback(delegate).call("input", context));
        String logs = output.getOut();
        assertTrue(logs.contains("result=result " + "x".repeat(493) + "..."));
        assertFalse(logs.contains("x".repeat(494)));
    }

    private static ToolCallback wrap(ToolCallback[] callbacks, String name) {
        return new ActivityLoggingToolCallback(Arrays.stream(callbacks)
                .filter(callback -> callback.getToolDefinition().name().equals(name))
                .findFirst().orElseThrow());
    }

    private static int count(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }
}
