package com.minerva.advisors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * Logs the current user message and the assistant's completed response.
 * Message text can contain sensitive information and is written to the application log.
 * Streamed responses are logged with a bounded prefix without changing the emitted stream.
 */
public class LoggerAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(LoggerAdvisor.class);
    static final int MAX_STREAM_LOG_CHARS = 10_000;

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }


    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        logRequest(chatClientRequest);

        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

        logResponse(chatClientResponse);

        return chatClientResponse;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);

        return Flux.defer(() -> {
            logRequest(chatClientRequest);
            StringBuilder response = new StringBuilder();
            boolean[] truncated = {false};
            return chatClientResponses
                    .doOnNext(chunk -> {
                        String text = responseText(chunk);
                        if (text != null) {
                            int remaining = MAX_STREAM_LOG_CHARS - response.length();
                            response.append(text, 0, Math.min(remaining, text.length()));
                            truncated[0] |= text.length() > remaining;
                        }
                    })
                    .doOnComplete(() -> {
                        if (truncated[0] && !response.isEmpty()
                                && Character.isHighSurrogate(response.charAt(response.length() - 1))) {
                            response.setLength(response.length() - 1);
                        }
                        logger.info("Assistant response: {}{}", response, truncated[0] ? " [truncated]" : "");
                    });
        });
    }

    private void logRequest(ChatClientRequest request) {
        logger.info("User message: {}", request.prompt().getUserMessage().getText());
    }

    private void logResponse(ChatClientResponse chatClientResponse) {
        String text = responseText(chatClientResponse);
        if (text != null) {
            logger.info("Assistant response: {}", text);
        }
    }

    private String responseText(ChatClientResponse response) {
        var chatResponse = response.chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) {
            return null;
        }
        return chatResponse.getResult().getOutput().getText();
    }
}
