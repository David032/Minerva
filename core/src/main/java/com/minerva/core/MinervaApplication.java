package com.minerva.core;

import com.minerva.discord.DiscordListener;
import com.minerva.utils.FileManager;
import com.minerva.utils.OllamaConnector;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.Objects;

import static java.lang.IO.*;

@SpringBootApplication(scanBasePackages = {
        "com.minerva.core", "com.minerva.api", "com.minerva.skills",
        "com.minerva.tools"
})
@EnableScheduling
public class MinervaApplication {

    static void main(String[] args) {
        var app = SpringApplication.run(MinervaApplication.class, args);
        var env = Objects.requireNonNull(app).getEnvironment();
        OllamaConnector.getInstance(env.getProperty("spring.ai.ollama.base-url"))
                .connectToOllama(env.getProperty("spring.ai.ollama.base-url"), env.getProperty("ollama.required-model"));

        if (System.getenv("DISCORD_TOKEN") != null) {
            JDA api = JDABuilder.createDefault(System.getenv("DISCORD_TOKEN")).build();
            api.addEventListener(new DiscordListener());
        }

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

    @Bean
    FileManager fileManager() {
        return FileManager.getInstance();
    }

    @Bean
    Clock reminderClock() {
        return Clock.systemUTC();
    }

    @Bean
    HeartbeatAssistant heartbeatAssistant() {
        return ConversationManager::sendHeartbeat;
    }
}
