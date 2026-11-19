package com.minerva.core.journiv;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("Unit")
class JournivToolsTests {
    private final JournivClient client = mock(JournivClient.class);
    private final JournivTools tools = new JournivTools(client);

    @Test
    void returnsPriorEntriesFromTheRequestedPage() {
        when(client.readEntries(2, 1)).thenReturn(List.of("Previous entry"));

        assertEquals("Previous entry", tools.readJournalEntries(2, 1));
    }

    @Test
    void doesNotExposeServerErrorBodiesToTheAssistant() {
        var error = new RestClientResponseException("failure", HttpStatusCode.valueOf(422), "Unprocessable",
                null, "private credential".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(client.readEntries(0, 1)).thenThrow(error);

        var thrown = assertThrows(JournivException.class, () -> tools.readJournalEntries(0, 1));
        assertEquals("Journiv request failed with HTTP 422", thrown.getMessage());
    }
}
