package com.minerva.core;

import com.minerva.discord.DiscordListener;
import com.minerva.utils.FileManager;
import com.minerva.utils.OllamaConnector;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
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
        "com.minerva.tools", "com.minerva.discord"
})
@EnableScheduling
public class MinervaApplication {

    static void main(String[] args) {
        var app = SpringApplication.run(MinervaApplication.class, args);
        var env = Objects.requireNonNull(app).getEnvironment();

        startDiscord(System.getenv("DISCORD_TOKEN"));

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

    /**
     * Starts the Discord listener when a non-blank bot token is configured.
     *
     * @param token the Discord bot token, or {@code null} to disable the integration
     */
    static void startDiscord(String token) {
        if (token == null || token.isBlank()) {
            print("Discord token has not been set!");
            return;
        }

        print("Discord token has been set!");
        JDA api = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT).build();
        api.addEventListener(new DiscordListener());
        print(api.getStatus());
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
