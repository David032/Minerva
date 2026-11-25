package com.minerva.advisors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("Unit")
class LoggerAdvisorTests {
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggerAdvisor.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final LoggerAdvisor advisor = new LoggerAdvisor();
    private Level originalLevel;

    @BeforeEach
    void captureLogs() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void restoreLogger() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
        appender.stop();
    }

    @Test
    void logsCurrentUserMessageAndCallResponseWithoutContext() {
        var request = new ChatClientRequest(new Prompt(List.of(
                new SystemMessage("private system prompt"),
                new UserMessage("old message"),
                new UserMessage("current message"))), Map.of("secret", "private context"));
        var response = response("assistant reply");
        var chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(request)).thenReturn(response);

        assertSame(response, advisor.adviseCall(request, chain));
        assertEquals(List.of("User message: current message", "Assistant response: assistant reply"), messages());
    }

    @Test
    void logsAggregatedStreamWithoutChangingItsElements() {
        var request = new ChatClientRequest(new Prompt("hello"), Map.of());
        var first = response("hel");
        var contextOnly = new ChatClientResponse(null, Map.of("key", "value"));
        var second = response("lo");
        var chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(request)).thenReturn(Flux.just(first, contextOnly, second));

        var stream = advisor.adviseStream(request, chain);
        assertEquals(List.of(), messages());
        assertEquals(List.of(first, contextOnly, second), stream.collectList().block());
        assertEquals(List.of("User message: hello", "Assistant response: hello"), messages());
    }

    @Test
    void doesNotLogACompletedResponseWhenStreamFails() {
        var request = new ChatClientRequest(new Prompt("hello"), Map.of());
        var chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(request)).thenReturn(Flux.concat(
                Flux.just(response("partial")), Flux.error(new IllegalStateException("failed"))));

        assertThrows(IllegalStateException.class, () -> advisor.adviseStream(request, chain).collectList().block());
        assertEquals(List.of("User message: hello"), messages());
    }

    @Test
    void truncatesAcrossChunksWithoutChangingStream() {
        var request = new ChatClientRequest(new Prompt("hello"), Map.of());
        var first = response("a".repeat(LoggerAdvisor.MAX_STREAM_LOG_CHARS - 1));
        var second = response("bcdef");
        var chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(request)).thenReturn(Flux.just(first, second));

        assertEquals(List.of(first, second), advisor.adviseStream(request, chain).collectList().block());
        assertEquals("Assistant response: " + "a".repeat(LoggerAdvisor.MAX_STREAM_LOG_CHARS - 1)
                + "b [truncated]", messages().getLast());
    }

    @Test
    void logsExactLimitWithoutTruncationAndTruncatesOneOversizedChunk() {
        var request = new ChatClientRequest(new Prompt("hello"), Map.of());
        var chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(request)).thenReturn(Flux.just(response("a".repeat(LoggerAdvisor.MAX_STREAM_LOG_CHARS))),
                Flux.just(response("b".repeat(LoggerAdvisor.MAX_STREAM_LOG_CHARS + 1))));

        advisor.adviseStream(request, chain).blockLast();
        advisor.adviseStream(request, chain).blockLast();

        assertEquals("Assistant response: " + "a".repeat(LoggerAdvisor.MAX_STREAM_LOG_CHARS), messages().get(1));
        assertEquals("Assistant response: " + "b".repeat(LoggerAdvisor.MAX_STREAM_LOG_CHARS) + " [truncated]",
                messages().getLast());
    }

    @Test
    void doesNotLogHalfOfASurrogatePairAtTheTruncationBoundary() {
        var request = new ChatClientRequest(new Prompt("hello"), Map.of());
        var chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(request)).thenReturn(Flux.just(
                response("a".repeat(LoggerAdvisor.MAX_STREAM_LOG_CHARS - 1) + "\uD83D"),
                response("\uDE00")));

        advisor.adviseStream(request, chain).blockLast();

        assertEquals("Assistant response: " + "a".repeat(LoggerAdvisor.MAX_STREAM_LOG_CHARS - 1)
                + " [truncated]", messages().getLast());
    }

    @Test
    void streamStateIsIndependentForEachSubscriptionAndCancellationDoesNotLogCompletion() {
        var request = new ChatClientRequest(new Prompt("hello"), Map.of());
        var chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(request)).thenReturn(Flux.just(response("one"), response("two")));
        var stream = advisor.adviseStream(request, chain);
        stream.take(1).blockLast();
        stream.collectList().block();
        stream.collectList().block();
        assertEquals(List.of("User message: hello", "User message: hello", "Assistant response: onetwo",
                "User message: hello", "Assistant response: onetwo"), messages());
    }

    @Test
    void handlesAResponseWithoutGenerations() {
        var request = new ChatClientRequest(new Prompt("hello"), Map.of());
        var response = new ChatClientResponse(new ChatResponse(List.of()), Map.of());
        var chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(request)).thenReturn(response);

        assertSame(response, advisor.adviseCall(request, chain));
        assertEquals(List.of("User message: hello"), messages());
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static ChatClientResponse response(String text) {
        return new ChatClientResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage(text)))), Map.of());
    }
}
