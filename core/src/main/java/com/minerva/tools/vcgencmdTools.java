package com.minerva.tools;

import org.springframework.ai.tool.annotation.Tool;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class vcgencmdTools {

    @Tool(description = "Get the current temperature of the CPU")
    String getTemperature() {
        if (isNotRaspberryPi()) {
            return "This tool is only available on Raspberry Pi devices.";
        }

        try {
            String[] command = {"/bin/sh", "-c", "vcgencmd measure_temp"};
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
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
        if (isNotRaspberryPi()) {
            return "This tool is only available on Raspberry Pi devices.";
        }

        try {
            String[] command = {"/bin/sh", "-c", "vcgencmd measure_clock arm"};
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null && line.startsWith("frequency(")) {
                var hz = line.substring(line.indexOf('=') + 1); // Extract the frequency value in Hz
                return String.format("CPU Frequency: %s Hz (%.2f MHz)", hz, Double.parseDouble(hz) / 1_000_000);
            } else {
                return "Unable to read clock frequency.";
            }
        } catch (IOException e) {
            return "Error executing vcgencmd: " + e.getMessage();
        }
    }

    @Tool(description = "Get the current GPU frequency")
    String getGpuFrequency() {
        if (isNotRaspberryPi()) {
            return "This tool is only available on Raspberry Pi devices.";
        }

        try {
            String[] command = {"/bin/sh", "-c", "vcgencmd measure_clock core"};
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null && line.startsWith("frequency(")) {
                var hz = line.substring(line.indexOf('=') + 1); // Extract the frequency value in Hz
                return String.format("GPU Frequency: %s Hz (%.2f MHz)", hz, Double.parseDouble(hz) / 1_000_000);
            } else {
                return "Unable to read clock frequency.";
            }
        } catch (IOException e) {
            return "Error executing vcgencmd: " + e.getMessage();
        }
    }

    @Tool(description = "Check if it is currently throttled, and if so, what the reason is")
    String getThrottledState(){

        if (isNotRaspberryPi()) {
            return "This tool is only available on Raspberry Pi devices.";
        }

        try {
            String[] command = {"/bin/sh", "-c", "vcgencmd get_throttled"};
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
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
        if (isNotRaspberryPi()) {
            return "This tool is only available on Raspberry Pi devices.";
        }
        String targetPlatform = System.getenv("IS_PI_5");
        if (targetPlatform == null || !targetPlatform.equals("True")) {
            return "This tool is only available on Raspberry Pi 5 devices.";
        }

        try {
            String[] command = {"/bin/sh", "-c", "vcgencmd pmic_read_adc EXT5V_V"};
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null && line.startsWith("EXT5V_V=")) {
                return "Power supply state: " + line.substring(8);
            } else {
                return "Unable to read power supply state.";
            }
        } catch (IOException e) {
            return "Error executing vcgencmd: " + e.getMessage();
        }
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
