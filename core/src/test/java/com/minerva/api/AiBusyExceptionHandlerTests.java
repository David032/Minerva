package com.minerva.api;

import com.minerva.core.AiBusyException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("Unit")
class AiBusyExceptionHandlerTests {
    @Test
    void returnsRetryMessageWithServiceUnavailableStatus() {
        var response = new AiBusyExceptionHandler().handleAiBusy(new AiBusyException());

        assertEquals(503, response.getStatusCode().value());
        assertThat(Arrays.asList(AiBusyException.MESSAGES).contains(response.getBody());
    }
}
