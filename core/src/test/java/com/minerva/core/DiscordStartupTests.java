package com.minerva.core;

import com.minerva.discord.DiscordListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class DiscordStartupTests {
    @Test
    void doesNotConnectWithoutAToken() {
        try (var builders = mockStatic(JDABuilder.class)) {
            MinervaApplication.startDiscord(null);
            MinervaApplication.startDiscord("");
            MinervaApplication.startDiscord(" \t ");

            builders.verifyNoInteractions();
        }
    }

    @Test
    void connectsAndRegistersListenerWithConfiguredToken() {
        var builder = mock(JDABuilder.class);
        var api = mock(JDA.class);
        try (var builders = mockStatic(JDABuilder.class)) {
            builders.when(() -> JDABuilder.createDefault("test-token")).thenReturn(builder);
            when(builder.enableIntents(GatewayIntent.MESSAGE_CONTENT)).thenReturn(builder);
            when(builder.build()).thenReturn(api);

            MinervaApplication.startDiscord("test-token");

            builders.verify(() -> JDABuilder.createDefault("test-token"));
            verify(builder).enableIntents(GatewayIntent.MESSAGE_CONTENT);
            verify(api).addEventListener(any(DiscordListener.class));
        }
    }
}
