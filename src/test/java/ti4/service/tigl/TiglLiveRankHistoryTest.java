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
 * Runs the rank-at-an-instant walk against a real response captured from /api/Tigl/tigl-player-rank-history on
 * 2026-10-11, for seven accounts supplied by the league owner.
 *
 * <p>The load-bearing check is that walking the history up to <em>now</em> reproduces the {@code currentRanks} block
 * the API reports separately - the same values the public rankings page renders. If our walk disagreed with that,
 * every rank we record would be suspect, and a hand-built fixture cannot catch it because then both sides of the
 * comparison are invented.
 *
 * <p>The capture deliberately includes an account with no TIGL profile at all (empty current ranks, zero history
 * entries) and two players sitting at Unranked on the Fractured ladder.
 */
class TiglLiveRankHistoryTest {

    private static final long NIUGNIP = 339399043740467200L;
    private static final long LAZIK = 206450549371961346L;
    private static final long NO_PROFILE = 222037635638493185L;

    // Comfortably after every entry in the capture, so the walk has to reach each account's newest rank.
    private static final long NOW = epochOf("2026-10-12");

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
    void walkingEachHistoryToNowReproducesTheCurrentRanks() {
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
        Optional<String> walked = TiglRankHistoryService.rankAtTimestamp(history, league, NOW);
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
        assertThat(TiglRankHistoryService.rankAtTimestamp(none, TiglRankHistoryService.STANDARD_LEAGUE, NOW))
                .isEmpty();
        assertThat(TiglRankHistoryService.rankAtTimestamp(none, TiglRankHistoryService.FRACTURED_LEAGUE, NOW))
                .isEmpty();
    }

    // The whole reason for walking history rather than reading a current-rank lookup: a game that started months ago
    // must see the rank the player held then.
    @Test
    void anOlderGameSeesTheRankHeldAtThatTime() {
        TiglPlayerRankHistory niugnip = byUser.get(NIUGNIP);

        assertThat(rankOn(niugnip, "Standard", "2025-11-30")).isEmpty();
        assertThat(rankOn(niugnip, "Standard", "2026-01-01")).contains("Unranked");
        assertThat(rankOn(niugnip, "Standard", "2026-02-01")).contains("Minister");
        assertThat(rankOn(niugnip, "Standard", "2026-03-15")).contains("Agent");
        assertThat(rankOn(niugnip, "Standard", "2026-06-01")).contains("Commander");
    }

    // niugnip reached Commander at 2026-04-14T20:00:27.324Z. Under the old date-only comparison the whole of
    // 2026-04-14 counted as Commander, so a game created that morning read twenty hours too generously.
    @Test
    void aRankUpLaterInTheDayDoesNotApplyToAGameStartedThatMorning() {
        TiglPlayerRankHistory niugnip = byUser.get(NIUGNIP);
        long commanderEarned = 1776196827324L;

        assertThat(TiglRankHistoryService.rankAtTimestamp(niugnip, "Standard", commanderEarned))
                .contains("Commander");
        assertThat(TiglRankHistoryService.rankAtTimestamp(niugnip, "Standard", commanderEarned - 1))
                .contains("Agent");
        assertThat(rankOn(niugnip, "Standard", "2026-04-14")).contains("Agent");
        assertThat(rankOn(niugnip, "Standard", "2026-04-15")).contains("Commander");
    }

    @Test
    void theTwoLaddersAreTrackedIndependently() {
        TiglPlayerRankHistory lazik = byUser.get(LAZIK);

        // On 2026-04-01 Lazik was Agent on Standard but already Thrall on Fractured.
        assertThat(rankOn(lazik, "Standard", "2026-04-01")).contains("Agent");
        assertThat(rankOn(lazik, "Fractured", "2026-04-01")).contains("Thrall");
    }

    // Legacy entries are present in the payload for several accounts and must never leak into either live ladder.
    @Test
    void legacyEntriesAreNeverReturnedForTheLiveLadders() {
        TiglPlayerRankHistory lazik = byUser.get(LAZIK);

        assertThat(lazik.getRanks()).anyMatch(entry -> "Legacy".equalsIgnoreCase(entry.getLeague()));
        assertThat(rankOn(lazik, "Standard", "2025-09-01")).isEmpty();
        assertThat(rankOn(lazik, "Fractured", "2025-09-01")).isEmpty();
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
