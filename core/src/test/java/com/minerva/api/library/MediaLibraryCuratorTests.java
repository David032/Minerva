package com.minerva.api.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("Unit")
class MediaLibraryCuratorTests {
    @Test
    void parsesValidatedCurationDecisions() {
        var decision = MediaLibraryCurator.parseDecision(
                "{\"accepted\":true,\"reason\":\"Strong match.\",\"summary\":\"An overview of astronomy.\"}");

        assertTrue(decision.accepted());
        assertEquals("Strong match.", decision.reason());
        assertEquals("An overview of astronomy.", decision.summary());
    }

    @Test
    void rejectsMalformedModelDecisions() {
        assertThrows(IllegalStateException.class, () -> MediaLibraryCurator.parseDecision(
                "{\"accepted\":\"yes\",\"reason\":\"Strong match.\",\"summary\":\"Description.\"}"));
        assertThrows(IllegalStateException.class, () -> MediaLibraryCurator.parseDecision(
                "{\"accepted\":true,\"reason\":\"Strong match.\"}"));
    }
}
