package ti4.service.tigl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ti4.json.JsonMapperManager;

/**
 * Drives the renderer from a captured API payload. The shape matches a live response from
 * /api/Tigl/tigl-player-rank-history, with two additions the live sample happened not to contain: a composite rank
 * name, and a Legacy-league entry.
 */
class TiglRankHistoryServiceTest {

    private static final long USER_ID = 111111111111111111L;

    private static TiglRankHistoryResponse response;

    @BeforeAll
    static void parseFixture() throws Exception {
        try (InputStream in = TiglRankHistoryServiceTest.class.getResourceAsStream("/tigl/rank-history.json")) {
            response = JsonMapperManager.basic().readValue(in, TiglRankHistoryResponse.class);
        }
    }

    @Test
    void parsesTheEnvelopeIncludingTheUnmodelledProblemDetailsField() {
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getItems()).hasSize(1);
        assertThat(response.getData().getItems().getFirst().getDiscordUserId()).isEqualTo(USER_ID);
    }

    // The bot only runs the Standard and Fractured ladders. Legacy is historical, so it is neither modelled nor
    // rendered - including it in the rank line implied the bot tracked a third ladder.
    @Test
    void showsOnlyTheTwoLaddersTheBotActuallyRuns() {
        String rendered = TiglRankHistoryService.renderMessage(List.of(USER_ID), response, false);

        assertThat(rendered).contains("Standard").contains("Fractured");
        assertThat(rendered).doesNotContain("Legacy");
    }

    @Test
    void omitsHistoryUnlessAskedFor() {
        String message = TiglRankHistoryService.renderMessage(List.of(USER_ID), response, false);
        assertThat(message).doesNotContain("2026-01-15");
    }

    // Composite rank names such as "Galactic Threat II (Hero)" do not map to any TIGLRank constant, so the renderer
    // has to pass them through untouched rather than resolving them.
    @Test
    void passesCompositeRankNamesThroughVerbatim() {
        String message = TiglRankHistoryService.renderMessage(List.of(USER_ID), response, true);
        assertThat(message).contains("Galactic Threat II (Hero)");
    }

    @Test
    void leavesLegacyLeagueEntriesOutOfTheHistory() {
        String message = TiglRankHistoryService.renderMessage(List.of(USER_ID), response, true);

        assertThat(message).contains("Standard").contains("Fractured");
        assertThat(message).doesNotContain("Legacy");
    }

    // Initial and legacy entries come back with an empty gameId; rendering a trailing separator for them looks broken.
    @Test
    void skipsEmptyGameIds() {
        String message = TiglRankHistoryService.renderMessage(List.of(USER_ID), response, true);
        assertThat(message).contains("`2025-12-01` Standard — Unranked (0 days)\n");
        assertThat(message).contains("`2026-01-15` Standard — Minister (45 days) · pbd1234");
    }

    @Test
    void singularDayIsNotPluralised() {
        String message = TiglRankHistoryService.renderMessage(List.of(USER_ID), response, true);
        assertThat(message).contains("(1 day)").doesNotContain("(1 days)");
    }

    // Per Lazik: currentRanks mixes prestige ranks with ladder ranks, and moves whenever the player ranks up - so a
    // game's "rank at start" has to come from walking the dated history for that league instead.
    @Test
    void resolvesTheRankHeldAtAPointInTimeRatherThanTheCurrentRank() {
        TiglPlayerRankHistory history = response.getData().getItems().getFirst();

        assertThat(history.getCurrentRanks().getStandard()).isEqualTo("Hero");

        // 2026-01-15 Minister, 2026-03-02 Galactic Threat II (Hero)
        assertThat(rankAt(history, TiglRankHistoryService.STANDARD_LEAGUE, "2026-02-01"))
                .contains("Minister");
        assertThat(rankAt(history, TiglRankHistoryService.STANDARD_LEAGUE, "2026-03-05"))
                .contains("Galactic Threat II (Hero)");
    }

    @Test
    void takesTheRankEarnedOnTheStartDayItself() {
        TiglPlayerRankHistory history = response.getData().getItems().getFirst();
        assertThat(rankAt(history, TiglRankHistoryService.STANDARD_LEAGUE, "2026-01-15"))
                .contains("Minister");
    }

    @Test
    void doesNotLeakRanksFromTheOtherLeague() {
        TiglPlayerRankHistory history = response.getData().getItems().getFirst();

        // The player is Acolyte in Fractured from 2026-04-21, but Standard must not see it.
        assertThat(rankAt(history, TiglRankHistoryService.STANDARD_LEAGUE, "2026-05-01"))
                .contains("Galactic Threat II (Hero)");
        assertThat(rankAt(history, TiglRankHistoryService.FRACTURED_LEAGUE, "2026-05-01"))
                .contains("Acolyte");
    }

    @Test
    void hasNoRankBeforeTheFirstEntry() {
        TiglPlayerRankHistory history = response.getData().getItems().getFirst();
        assertThat(rankAt(history, TiglRankHistoryService.FRACTURED_LEAGUE, "2026-01-01"))
                .isEmpty();
    }

    private static Optional<String> rankAt(TiglPlayerRankHistory history, String league, String date) {
        long epochMillis =
                LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        return TiglRankHistoryService.rankAtTimestamp(history, league, epochMillis);
    }

    @Test
    void reportsPlayersTheApiReturnedNoDataFor() {
        String message = TiglRankHistoryService.renderMessage(List.of(999999999999999999L), response, false);
        assertThat(message).contains("No TIGL data found.");
    }
}
