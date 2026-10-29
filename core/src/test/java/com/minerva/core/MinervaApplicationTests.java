package com.minerva.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;

@Import(MinervaTestConfiguration.class)
@SpringBootTest
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

}
