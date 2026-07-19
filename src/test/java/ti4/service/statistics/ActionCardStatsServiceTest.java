package ti4.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ActionCardStatsServiceTest {

    @Test
    void shouldUseTheClassMaxPlaysAsExpectedDraws() {
        Map<String, Integer> expectedDraws = ActionCardStatsService.computeExpectedDraws(
                Map.of("Sabotage", 100, "Direct Hit", 60, "Rise of a Messiah", 20),
                Map.of("Sabotage", 4, "Direct Hit", 4, "Rise of a Messiah", 1));

        assertThat(expectedDraws)
                .containsEntry("Sabotage", 100)
                .containsEntry("Direct Hit", 100)
                .containsEntry("Rise of a Messiah", 20);
    }

    @Test
    void shouldGiveEveryClassMemberTheFullClassMax() {
        Map<String, Integer> expectedDraws = ActionCardStatsService.computeExpectedDraws(
                Map.of("Sabotage", 20, "Overrule", 30), Map.of("Sabotage", 4, "Overrule", 4));

        assertThat(expectedDraws).containsEntry("Sabotage", 30).containsEntry("Overrule", 30);
    }

    @Test
    void shouldSkipCardsNotInTheDeck() {
        Map<String, Integer> expectedDraws = ActionCardStatsService.computeExpectedDraws(
                Map.of("Sabotage", 10, "Mystery Card", 5), Map.of("Sabotage", 4));

        assertThat(expectedDraws).containsOnlyKeys("Sabotage");
    }

    @Test
    void shouldSkipClassesWithNoPlays() {
        Map<String, Integer> expectedDraws = ActionCardStatsService.computeExpectedDraws(
                Map.of("Sabotage", 10), Map.of("Sabotage", 4, "Rise of a Messiah", 1));

        // The 1-of class has zero plays, so no expected draws can be derived for it.
        assertThat(expectedDraws).containsOnlyKeys("Sabotage");
    }

    @Test
    void shouldIncludeUnplayedCardsWhoseClassHasPlays() {
        Map<String, Integer> expectedDraws = ActionCardStatsService.computeExpectedDraws(
                Map.of("Sabotage", 10), Map.of("Sabotage", 4, "Direct Hit", 4));

        // Direct Hit was never played, but its class max still approximates its draws.
        assertThat(expectedDraws).containsEntry("Direct Hit", 10);
    }
}
