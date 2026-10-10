package ti4.service.tigl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ti4.json.JsonMapperManager;

/**
 * Runs the rank-at-a-date walk against a real response captured from /api/Tigl/tigl-player-rank-history on
 * 2026-09-30, for seven accounts supplied by the league owner.
 *
 * <p>The load-bearing check is that walking the dated history up to <em>today</em> reproduces the
 * {@code currentRanks} block the API reports separately - the same values the public rankings page renders. If our
 * walk disagreed with that, every rank we record would be suspect, and a hand-built fixture cannot catch it because
 * then both sides of the comparison are invented.
 *
 * <p>The capture deliberately includes an account with no TIGL profile at all (empty current ranks, zero history
 * entries) and two players sitting at Unranked on the Fractured ladder.
 */
class TiglLiveRankHistoryTest {

    private static final long NIUGNIP = 339399043740467200L;
    private static final long LAZIK = 206450549371961346L;
    private static final long NO_PROFILE = 222037635638493185L;

    private static final long TODAY = epochOf("2026-09-30");

    private static Map<Long, TiglPlayerRankHistory> byUser;

    @BeforeAll
    static void parseLiveCapture() throws Exception {
        try (InputStream in = TiglLiveRankHistoryTest.class.getResourceAsStream("/tigl/rank-history-live.json")) {
            TiglRankHistoryResponse response = JsonMapperManager.basic().readValue(in, TiglRankHistoryResponse.class);
            byUser = response.getData().getItems().stream()
                    .collect(Collectors.toMap(TiglPlayerRankHistory::getDiscordUserId, item -> item));
        }
    }

    @Test
    void theCaptureCoversAllSevenAccounts() {
        assertThat(byUser).hasSize(7).containsKeys(NIUGNIP, LAZIK, NO_PROFILE);
    }

    /**
     * The one check that validates our walk against the league's own numbers, across every account and both ladders.
     */
    @Test
    void walkingEachHistoryToTodayReproducesTheCurrentRanks() {
        for (Map.Entry<Long, TiglPlayerRankHistory> entry : byUser.entrySet()) {
            TiglPlayerRankHistory history = entry.getValue();
            TiglCurrentRanks current = history.getCurrentRanks();

            assertLadderMatches(entry.getKey(), history, TiglRankHistoryService.STANDARD_LEAGUE, current.getStandard());
            assertLadderMatches(
                    entry.getKey(), history, TiglRankHistoryService.FRACTURED_LEAGUE, current.getFractured());
        }
    }

    private static void assertLadderMatches(
            Long userId, TiglPlayerRankHistory history, String league, String currentRank) {
        Optional<String> walked = TiglRankHistoryService.rankAtTimestamp(history, league, TODAY);
        if (StringUtils.isBlank(currentRank)) {
            // An account with no profile on that ladder has nothing to walk to either.
            assertThat(walked)
                    .as("%s on %s has no current rank", userId, league)
                    .isEmpty();
        } else {
            assertThat(walked).as("%s on %s", userId, league).contains(currentRank);
        }
    }

    // A Discord account that has never played a TIGL game comes back with empty current ranks and an empty history.
    // The caller turns the empty Optional into UNRANKED rather than guessing or throwing.
    @Test
    void anAccountWithNoTiglProfileHasNoRankOnEitherLadder() {
        TiglPlayerRankHistory none = byUser.get(NO_PROFILE);

        assertThat(none.getRanks()).isEmpty();
        assertThat(none.getCurrentRanks().getStandard()).isEmpty();
        assertThat(rankOn(none, TiglRankHistoryService.STANDARD_LEAGUE, "2026-09-30"))
                .isEmpty();
        assertThat(rankOn(none, TiglRankHistoryService.FRACTURED_LEAGUE, "2026-09-30"))
                .isEmpty();
    }

    // The whole reason for reading dated history rather than a current-rank lookup: a game that started months ago
    // must see the rank the player held then. niugnip reached Agent on 2026-03-02 and Commander on 2026-04-14.
    @Test
    void anOlderGameSeesTheRankHeldAtThatTime() {
        TiglPlayerRankHistory niugnip = byUser.get(NIUGNIP);

        assertThat(rankOn(niugnip, "Standard", "2026-01-19")).contains("Unranked");
        assertThat(rankOn(niugnip, "Standard", "2026-01-20")).contains("Minister");
        assertThat(rankOn(niugnip, "Standard", "2026-03-15")).contains("Agent");
        assertThat(rankOn(niugnip, "Standard", "2026-04-13")).contains("Agent");
        assertThat(rankOn(niugnip, "Standard", "2026-04-14")).contains("Commander");
        assertThat(rankOn(niugnip, "Standard", "2026-09-30")).contains("Commander");
    }

    @Test
    void theTwoLaddersAreTrackedIndependently() {
        TiglPlayerRankHistory lazik = byUser.get(LAZIK);

        // On 2026-04-01 Lazik was Agent on Standard but already Thrall on Fractured.
        assertThat(rankOn(lazik, "Standard", "2026-04-01")).contains("Agent");
        assertThat(rankOn(lazik, "Fractured", "2026-04-01")).contains("Thrall");
    }

    @Test
    void thereIsNoRankBeforeTheFirstEntry() {
        assertThat(rankOn(byUser.get(NIUGNIP), "Standard", "2025-11-30")).isEmpty();
    }

    private static Optional<String> rankOn(TiglPlayerRankHistory history, String league, String date) {
        return TiglRankHistoryService.rankAtTimestamp(history, league, epochOf(date));
    }

    private static long epochOf(String date) {
        return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}
