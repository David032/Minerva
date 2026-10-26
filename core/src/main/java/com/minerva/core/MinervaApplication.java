package com.minerva.core;

import io.github.ollama4j.exceptions.OllamaException;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Fallback;
import org.springframework.web.client.RestClient;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.lang.IO.*;

@SpringBootApplication()
public class MinervaApplication {

    public static void main(String[] args) throws OllamaException {

        var app = SpringApplication.run(MinervaApplication.class, args);
        var env = Objects.requireNonNull(app).getEnvironment();
        connectToOllama(env.getProperty("spring.ai.ollama.base-url"), env.getProperty("ollama.required-model"));
        if (Objects.equals(System.getenv("TERMINAL_INPUT"), "true")){
            while (true) {
                print("> ");
                var line = readln();
                println();
                print(ConversationManager.sendMessage(line));
                println();
            }
        }

    }

    @Bean
    ChatMemory jdbcChatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .build();
    }



    static void connectToOllama(String url, String aimodel){
        RestClient ollamaClient = RestClient.builder().baseUrl(url).build();

        //Check to see if model is already present
        var initialCheck = ollamaClient.get().uri(url + "/api/tags").retrieve().toEntity(String.class);
        assert initialCheck.getBody() != null;
        boolean isAlreadyPresent = initialCheck.getBody().contains(aimodel);
        print("Is model already present? " + isAlreadyPresent);
        if (!isAlreadyPresent) {
            //Pull model
            Dictionary<String, String> body = new Hashtable<>();
            body.put("model", aimodel);

            var pullModel = ollamaClient.post().uri(url + "/api/pull").body(body).retrieve().toEntity(String.class);
            assert pullModel.getBody() != null;
            print("Model pulled?" + pullModel.getBody());

            boolean isAvailable = false;
            while (!isAvailable) {
                print("Checking for model...");
                var checkIfReady = ollamaClient.get().uri(url + "/api/tags").retrieve().toEntity(String.class);
                assert checkIfReady.getBody() != null;
                if (checkIfReady.getBody().contains(aimodel)) {
                    isAvailable = true;
                    print("Model ready!");
                } else {
                    print("Model not ready!");
                    try {
                        TimeUnit.SECONDS.sleep(90);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
}
