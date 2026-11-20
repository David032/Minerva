package com.minerva.api.personality;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("Unit")
class PersonalityAnalyzerTests {
    @Test
    void parsesNumberedGoalsAndRejectsInvalidGoalSets() {
        assertEquals(List.of("Explore local history", "Learn a practical skill", "Build a useful habit"),
                PersonalityAnalyzer.parseGoals("""
                        1. Explore local history
                        2) Learn a practical skill
                        - Build a useful habit
                        """));

        assertThrows(IllegalStateException.class,
                () -> PersonalityAnalyzer.parseGoals("1. One goal\n2. Two goals"));
        assertThrows(IllegalStateException.class,
                () -> PersonalityAnalyzer.parseGoals("1. Same goal\n2. Same goal\n3. Another goal"));
    }
}
