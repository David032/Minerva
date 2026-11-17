package com.minerva.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Indicates that the assistant is temporarily unavailable because a heartbeat is running.
 */
public class AiBusyException extends RuntimeException {
    public static final String USER_MESSAGE =
            "Minerva is running a heartbeat right now. Please try again shortly.";

    public static final String[] MESSAGES = {USER_MESSAGE, "I'm busy right now", "Can't talk right now, busy", "Talk later?", "Can't talk, busy", "Not right now please"};

    /**
     * Creates the standard temporary-busy response.
     */
    public AiBusyException() {
        int randomSelection =  new Random().nextInt(MESSAGES.length);
        super(MESSAGES[randomSelection]);
    }
}
