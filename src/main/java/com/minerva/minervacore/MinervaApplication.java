package com.minerva.minervacore;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

import static java.lang.IO.*;

@SpringBootApplication()
public class MinervaApplication {

    public static void main(String[] args) {

        SpringApplication.run(MinervaApplication.class, args);

        while (true) {
            print("> ");
            var line = readln();
            println();
            print(ConversationManager.sendMessage(line));
            println();
        }
    }

    @Bean
    ChatMemory jdbcChatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .build();
    }
}
