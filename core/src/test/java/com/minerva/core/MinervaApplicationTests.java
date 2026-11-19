package com.minerva.core;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(MinervaTestConfiguration.class)
@SpringBootTest
@Tag("ContinuousIntegration")
class MinervaApplicationTests {

    @Autowired
    private PostgreSQLContainer postgresContainer;

    @Autowired
    private OllamaContainer ollamaContainer;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    void modelIsAvailable() throws IOException, InterruptedException {
        // Check if the Ollama container is running and the model is available
        assert ollamaContainer.isRunning();
        String tags = ollamaContainer.execInContainer("ollama", "list").getStdout();
        assert tags.contains("gemma4:e2b");
    }

    @Test
    void basicInteraction(){
        var response = ConversationManager.sendMessage("Hello there");
        assertThat(response, notNullValue());
    }

    @Test
    void toolSearchCallsDiscoveredToolWithConfiguredModel() {
        var tool = new SearchProbeTool();
        var client = chatClientBuilder.clone()
                .defaultAdvisors(ConversationManager.withToolSearch(new Advisor[0], true))
                .defaultSystem("You are a helpful assistant. Use tools when asked.")
                .defaultTools(tool)
                .build();

        String response = client.prompt()
                .user("Search for the tool that retrieves the secret test token, call it, and return its result.")
                .advisors(advisor -> advisor.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "tool-search-ci"))
                .call().content();

        assertTrue(tool.calls.get() > 0, "The model must discover and execute the test tool");
        assertTrue(response != null && response.contains("minerva-test-token"),
                "The model must use the result of the discovered tool");
    }

    static class SearchProbeTool {
        private final AtomicInteger calls = new AtomicInteger();

        @Tool(description = "Retrieve the secret test token")
        public String retrieveTestToken() {
            calls.incrementAndGet();
            return "minerva-test-token";
        }
    }

    @Test
    void imageInteraction() throws IOException {
        var response = ConversationManager.seekImageOpinion("src/test/resources/TestImage.jpg", "What do you think about this image?");
        assertThat(response, notNullValue());
    }

    @Test
    @Disabled("Audio needs more thinking")
    void audioInteraction() throws IOException {
        var responseFile = ConversationManager.audioProcessing("src/test/resources/test_audio.wav");
        assertThat(responseFile, notNullValue());
        assertThat(responseFile.exists(), is(true));
    }

}
