package com.minerva.core;

import com.minerva.utils.OllamaConnector;
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

    public static void main(String[] args) {
        var app = SpringApplication.run(MinervaApplication.class, args);
        var env = Objects.requireNonNull(app).getEnvironment();
        OllamaConnector.getInstance(env.getProperty("spring.ai.ollama.base-url"))
                .connectToOllama(env.getProperty("spring.ai.ollama.base-url"), env.getProperty("ollama.required-model"));
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
}
