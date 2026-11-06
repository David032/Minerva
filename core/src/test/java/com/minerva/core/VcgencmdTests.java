package com.minerva.core;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Import(MinervaTestConfiguration.class)
@SpringBootTest
public class VcgencmdTests {

    @Test
    void testGetTemperature() {
        var response = ConversationManager.sendMessage("What is the current CPU temperature?");
        assertThat(response, containsString("'C"));
    }

    @Test
    void testGetCPUFrequency() {
        var response = ConversationManager.sendMessage("What is the current CPU frequency?");
        assertThat(response, containsString("CPU Frequency:"));
        assertThat(response, containsString("Hz"));
        assertThat(response, containsString("MHz"));
    }

    @Test
    void testGetGpuFrequency() {
        var response = ConversationManager.sendMessage("What is the current GPU frequency?");
        assertThat(response, containsString("GPU Frequency:"));
        assertThat(response, containsString("Hz"));
        assertThat(response, containsString("MHz"));
    }

    @Test
    void getPowerSupplyState() {
        var response = ConversationManager.sendMessage("What is the current power supply state?");
        assertThat(response, containsString("Power Supply State:"));
        assertThat(response, containsString("5."));
    }

}
