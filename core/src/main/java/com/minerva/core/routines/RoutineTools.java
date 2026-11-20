package com.minerva.core.routines;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.stream.Collectors;

/** Assistant tools for managing and inspecting recurring routines. */
@Component
public class RoutineTools {
    private final RoutineService service;

    /** Creates the tool collection. */
    public RoutineTools(RoutineService service) { this.service = service; }

    /** Creates an enabled routine using the shared heartbeat schedule. */
    @Tool(description = "Create an enabled recurring routine. It runs on an idle heartbeat tick, not on a custom schedule. Provide a name and explicit instructions.")
    public String createRoutine(String name, String instructions) {
        return describe(service.create(name, instructions));
    }

    /** Lists enabled and disabled routines. */
    @Tool(description = "List recurring routines, including disabled routines.")
    public String listRoutines() {
        var routines = service.list();
        return routines.isEmpty() ? "No routines." : routines.stream().map(RoutineTools::describe).collect(Collectors.joining("\n"));
    }

    /** Retrieves a routine and its instructions. */
    @Tool(description = "Get a recurring routine by UUID, including its instructions and enabled state.")
    public String getRoutine(String routineId) { return describe(service.get(UUID.fromString(routineId))); }

    /** Replaces a routine's definition and enabled state. */
    @Tool(description = "Update a recurring routine by UUID. Supply its complete name, instructions, and enabled state.")
    public String updateRoutine(String routineId, String name, String instructions, boolean enabled) {
        return describe(service.update(UUID.fromString(routineId), name, instructions, enabled));
    }

    /** Disables a routine while retaining its history. */
    @Tool(description = "Disable a recurring routine by UUID; its history is retained.")
    public String disableRoutine(String routineId) { return describe(service.disable(UUID.fromString(routineId))); }

    /** Lists the latest 20 attempts, including failures and interruptions. */
    @Tool(description = "Show the latest 20 attempts and outcomes for a recurring routine by UUID.")
    public String routineHistory(String routineId) {
        var history = service.history(UUID.fromString(routineId));
        return history.isEmpty() ? "No attempts." : history.stream()
                .map(run -> run.getStartedAt() + " " + run.getStatus() + " " + run.getOutcome())
                .collect(Collectors.joining("\n"));
    }

    private static String describe(Routine r) {
        return r.getId() + " | " + r.getName() + " | enabled=" + r.isEnabled()
                + " | last started=" + r.getLastStartedAt() + " | instructions: " + r.getInstructions();
    }
}
