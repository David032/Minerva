package com.minerva.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("Unit")
class VcgencmdTests {

    @Test
    void reportsWhenHardwareIsUnavailableWithoutRunningCommand() {
        var tool = new vcgencmdTools(() -> true, command -> {
            throw new AssertionError("Command must not run on unsupported hardware");
        });

        assertEquals("This tool is only available on Raspberry Pi devices.", tool.getTemperature());
        assertEquals("This tool is only available on Raspberry Pi devices.", tool.getCPUFrequency());
        assertEquals("This tool is only available on Raspberry Pi devices.", tool.getGpuFrequency());
        assertEquals("This tool is only available on Raspberry Pi devices.", tool.getThrottledState());
    }

    @Test
    void formatsTemperatureAndFrequencies() {
        var command = new AtomicReference<String>();
        var tool = new vcgencmdTools(() -> false, requestedCommand -> {
            command.set(requestedCommand);
            return switch (requestedCommand) {
                case "vcgencmd measure_temp" -> "temp=42.5'C";
                case "vcgencmd measure_clock arm" -> "frequency(48)=1500000";
                case "vcgencmd measure_clock core" -> "frequency(1)=600000";
                default -> null;
            };
        });

        assertEquals("42.5'C", tool.getTemperature());
        assertEquals("vcgencmd measure_temp", command.get());
        assertEquals("CPU Frequency: 1500000 Hz (1.50 MHz)", tool.getCPUFrequency());
        assertEquals("GPU Frequency: 600000 Hz (0.60 MHz)", tool.getGpuFrequency());
    }

    @Test
    void mapsThrottlingStatesAndUnknownValues() {
        var output = new AtomicReference<>("throttled=0x0");
        var tool = new vcgencmdTools(() -> false, command -> output.get());

        assertEquals("No throttling.", tool.getThrottledState());
        output.set("throttled=0x4");
        assertEquals("Arm frequency capped.", tool.getThrottledState());
        output.set("throttled=0x9");
        assertEquals("Unknown throttled state: 0x9", tool.getThrottledState());
    }

    @Test
    void reportsMalformedOutputAndCommandFailures() {
        var malformedOutputTool = new vcgencmdTools(() -> false, command -> "unexpected");
        assertEquals("Unable to read temperature.", malformedOutputTool.getTemperature());

        var failingTool = new vcgencmdTools(() -> false, command -> {
            throw new IOException("command failed");
        });
        assertEquals("Error executing vcgencmd: command failed", failingTool.getTemperature());
    }

    @Test
    void powerSupplyIsUnavailableWhenNotOnPi() {
        var tool = new vcgencmdTools(() -> true, command -> {
            throw new AssertionError("Command must not run on unsupported hardware");
        });

        assertEquals("This tool is only available on Raspberry Pi devices.", tool.getPowerSupplyState());
    }

    @Test
    void reportsPowerSupplyForPi5AndRejectsOtherPiModels() {
        var tool = new vcgencmdTools(() -> false, () -> false, command -> {
            throw new AssertionError("Command must not run on non-Pi-5 hardware");
        });
        assertEquals("This tool is only available on Raspberry Pi 5 devices.", tool.getPowerSupplyState());

        var pi5Tool = new vcgencmdTools(() -> false, () -> true, command -> "EXT5V_V=5.1V");
        assertEquals("Power supply state: 5.1V", pi5Tool.getPowerSupplyState());
    }
}
