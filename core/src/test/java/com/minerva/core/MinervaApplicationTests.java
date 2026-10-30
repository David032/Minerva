package com.minerva.core;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
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

@Import(MinervaTestConfiguration.class)
@SpringBootTest
@Tag("ContinuousIntegration")
class MinervaApplicationTests {

    @Autowired
    private PostgreSQLContainer postgresContainer;

    @Autowired
    private OllamaContainer ollamaContainer;

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

    @Test
    void alarmInteraction() {
        String alarmTime = "2024-06-30T15:30:00";
        var response = ConversationManager.sendMessage("Set an alarm for " + alarmTime);
        assertThat(response, stringContainsInOrder("Alarm", "set", "for", "2024-06-30", "15:30"));
    }

}
