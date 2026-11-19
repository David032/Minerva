package com.minerva.core;

import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Map;
import java.util.Set;

/**
 * Records heartbeat tool invocations and successful long-term memory mutations.
 * Inputs and memory-tool outputs are excluded from the log.
 */
final class ActivityLoggingToolCallback implements ToolCallback {
    private static final Logger logger = LoggerFactory.getLogger(ActivityLoggingToolCallback.class);
    private static final Map<String, String> MEMORY_OPERATIONS = Map.of(
            "MemoryCreate", "added",
            "MemoryStrReplace", "updated",
            "MemoryInsert", "updated",
            "MemoryDelete", "deleted",
            "MemoryRename", "renamed");
    private static final Set<String> MEMORY_TOOLS = Set.of(
            "MemoryView", "MemoryCreate", "MemoryStrReplace", "MemoryInsert", "MemoryDelete", "MemoryRename");

    private final ToolCallback delegate;

    ActivityLoggingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String input) {
        return invoke(input, null, false);
    }

    @Override
    public String call(String input, ToolContext context) {
        return invoke(input, context, true);
    }

    private String invoke(String input, ToolContext context, boolean withContext) {
        String name = getToolDefinition().name();
        HeartbeatRun run = context != null && context.getContext().get(HeartbeatRun.CONTEXT_KEY) instanceof HeartbeatRun value
                ? value : null;
        if (run != null) {
            run.actionStarted();
            logger.info("Heartbeat run={} tool={} started", run.id(), name);
        }
        try {
            String result = withContext ? delegate.call(input, context) : delegate.call(input);
            String memoryResult = MEMORY_TOOLS.contains(name) ? memoryResult(result) : null;
            if (MEMORY_OPERATIONS.containsKey(name) && "success".equals(memoryResult)
                    && !isUnchangedReplacement(name, input)) {
                logger.info("Memory {} via={}{}", MEMORY_OPERATIONS.get(name), name,
                        run == null ? "" : " heartbeat=" + run.id());
            }
            if (run != null) {
                logger.info("Heartbeat run={} tool={} outcome={} outputCharacters={} result={}", run.id(), name,
                        memoryResult == null ? "returned" : memoryResult, result == null ? 0 : result.length(),
                        MEMORY_TOOLS.contains(name) ? "<memory output omitted>" : ActivityLogText.bounded(result));
            }
            return result;
        } catch (RuntimeException exception) {
            if (run != null) {
                logger.error("Heartbeat run={} tool={} outcome=exception type={}", run.id(), name,
                        exception.getClass().getSimpleName());
            }
            throw exception;
        }
    }

    private static String memoryResult(String result) {
        try {
            var value = JsonParser.parseString(result);
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String text = value.getAsString();
                if (text.startsWith("Successfully ")) {
                    return "success";
                }
                if (text.startsWith("Error")) {
                    return "error";
                }
            }
        } catch (RuntimeException exception) {
            logger.warn("Could not classify memory tool result ({})", exception.getClass().getSimpleName());
        }
        return "unclassified";
    }

    private static boolean isUnchangedReplacement(String name, String input) {
        if (!"MemoryStrReplace".equals(name)) {
            return false;
        }
        try {
            var arguments = JsonParser.parseString(input).getAsJsonObject();
            return arguments.has("oldStr") && arguments.has("newStr")
                    && arguments.get("oldStr").equals(arguments.get("newStr"));
        } catch (RuntimeException exception) {
            logger.warn("Could not classify memory replacement ({})", exception.getClass().getSimpleName());
            return true;
        }
    }
}
