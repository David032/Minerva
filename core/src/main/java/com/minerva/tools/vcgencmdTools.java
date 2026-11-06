package com.minerva.tools;

import org.springframework.ai.tool.annotation.Tool;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public class vcgencmdTools {
    private final BooleanSupplier notRaspberryPi;
    private final BooleanSupplier raspberryPi5;
    private final CommandRunner commandRunner;

    public vcgencmdTools() {
        this(vcgencmdTools::isNotRaspberryPi,
                () -> "True".equals(System.getenv("IS_PI_5")),
                vcgencmdTools::runCommand);
    }

    vcgencmdTools(BooleanSupplier notRaspberryPi, CommandRunner commandRunner) {
        this(notRaspberryPi, () -> "True".equals(System.getenv("IS_PI_5")), commandRunner);
    }

    vcgencmdTools(BooleanSupplier notRaspberryPi, BooleanSupplier raspberryPi5, CommandRunner commandRunner) {
        this.notRaspberryPi = notRaspberryPi;
        this.raspberryPi5 = raspberryPi5;
        this.commandRunner = commandRunner;
    }

    @Tool(description = "Get the current temperature of the CPU")
    String getTemperature() {
        if (notRaspberryPi.getAsBoolean()) {
            return "This tool is only available on Raspberry Pi devices.";
        }

        try {
            String line = commandRunner.run("vcgencmd measure_temp");
            if (line != null && line.startsWith("temp=")) {
                return line.substring(5); // Extract the temperature value
            } else {
                return "Unable to read temperature.";
            }
        } catch (IOException e) {
            return "Error executing vcgencmd: " + e.getMessage();
        }
    }

    @Tool(description = "Get the current CPU frequency")
    String getCPUFrequency() {
        if (notRaspberryPi.getAsBoolean()) {
            return "This tool is only available on Raspberry Pi devices.";
        }

        try {
            String line = commandRunner.run("vcgencmd measure_clock arm");
            if (line != null && line.startsWith("frequency(")) {
                var hz = line.substring(line.indexOf('=') + 1); // Extract the frequency value in Hz
                return String.format(Locale.ROOT, "CPU Frequency: %s Hz (%.2f MHz)", hz, Double.parseDouble(hz) / 1_000_000);
            } else {
                return "Unable to read clock frequency.";
            }
        } catch (IOException e) {
            return "Error executing vcgencmd: " + e.getMessage();
        }
    }

    @Tool(description = "Get the current GPU frequency")
    String getGpuFrequency() {
        if (notRaspberryPi.getAsBoolean()) {
            return "This tool is only available on Raspberry Pi devices.";
        }

        try {
            String line = commandRunner.run("vcgencmd measure_clock core");
            if (line != null && line.startsWith("frequency(")) {
                var hz = line.substring(line.indexOf('=') + 1); // Extract the frequency value in Hz
                return String.format(Locale.ROOT, "GPU Frequency: %s Hz (%.2f MHz)", hz, Double.parseDouble(hz) / 1_000_000);
            } else {
                return "Unable to read clock frequency.";
            }
        } catch (IOException e) {
            return "Error executing vcgencmd: " + e.getMessage();
        }
    }

    @Tool(description = "Check if it is currently throttled, and if so, what the reason is")
    String getThrottledState(){

        if (notRaspberryPi.getAsBoolean()) {
            return "This tool is only available on Raspberry Pi devices.";
        }

        try {
            String line = commandRunner.run("vcgencmd get_throttled");
            if (line != null && line.startsWith("throttled=")) {
                var state = line.substring(10);
                return switch (state) {
                    case "0x0" -> "No throttling.";
                    case "0x1" -> "Under-voltage detected.";
                    case "0x4" -> "Arm frequency capped.";
                    case "0x8" -> "Soft temperature limit active.";
                    case "0x10000" -> "Under-voltage has occurred.";
                    case "0x20000" -> "Arm frequency capping has occurred.";
                    case "0x40000" -> "Throttling has occurred.";
                    case "0x80000" -> "Soft temperature limit has occurred.";
                    default -> "Unknown throttled state: " + state;
                };
            } else {
                return "Unable to read throttled state.";
            }
        } catch (IOException e) {
            return "Error executing vcgencmd: " + e.getMessage();
        }
    }

    @Tool(description = "Get the current power supply state")
    String getPowerSupplyState(){
        if (notRaspberryPi.getAsBoolean()) {
            return "This tool is only available on Raspberry Pi devices.";
        }
        if (!raspberryPi5.getAsBoolean()) {
            return "This tool is only available on Raspberry Pi 5 devices.";
        }

        try {
            String line = commandRunner.run("vcgencmd pmic_read_adc EXT5V_V");
            if (line != null && line.startsWith("EXT5V_V=")) {
                return "Power supply state: " + line.substring(8);
            } else {
                return "Unable to read power supply state.";
            }
        } catch (IOException e) {
            return "Error executing vcgencmd: " + e.getMessage();
        }
    }

    private static String runCommand(String command) throws IOException {
        String[] shellCommand = {"/bin/sh", "-c", command};
        Process process = Runtime.getRuntime().exec(shellCommand);
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            return reader.readLine();
        }
    }

    @FunctionalInterface
    interface CommandRunner {
        String run(String command) throws IOException;
    }

    private static boolean isNotRaspberryPi() {
        String osName = System.getProperty("os.name").toLowerCase();

        // Raspberry Pi officially runs on Linux distributions
        if (!osName.contains("linux")) {
            return true;
        }

        // Check cpuinfo for hardware identifiers like "BCM" or "Raspberry"
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String lowerLine = line.toLowerCase();
                if (lowerLine.contains("raspberry") || lowerLine.contains("bcm")) {
                    return false;
                }
            }
        } catch (IOException e) {
            // Fallback: If /proc/cpuinfo is unreadable, check the os-release file
            return !checkOsRelease();
        }

        return true;
    }

    private static boolean checkOsRelease() {
        try (BufferedReader br = new BufferedReader(new FileReader("/etc/os-release"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.toLowerCase().contains("raspbian")) {
                    return true;
                }
            }
        } catch (IOException e) {
            // Ignore and fall through to return false
        }
        return false;
    }
}
