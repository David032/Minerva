package com.minerva.core;

import io.github.ollama4j.Ollama;
import io.github.ollama4j.exceptions.OllamaException;
import io.github.ollama4j.models.response.Model;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

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

    static void connectToOllama(String url, String aiModel) throws OllamaException {
        Ollama ollama = new Ollama(url);

        List<Model> models = ollama.listModels();
        for (Model model : models) {
            if(model.getModelName().equalsIgnoreCase(aiModel)) {
                print("Required model found!");
            }
        }

        print("Required model not found!");
        ollama.pullModel(aiModel);
        print("Model pulled!");
    }
}
