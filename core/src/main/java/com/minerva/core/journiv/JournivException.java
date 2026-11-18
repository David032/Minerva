package com.minerva.core.journiv;

/** An integration failure whose message contains no activity text or credentials. */
final class JournivException extends IllegalStateException {
    JournivException(String message) {
        super(message);
    }
}
