package ti4.spring.service.statistics.matchmaking.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gesundkrank.jskills.Rating;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.settings.users.UserSettings;
import ti4.spring.service.statistics.matchmaking.MatchmakingRatingEventService;
import ti4.testUtils.BaseTi4Test;

class MatchmakingGrouperTest extends BaseTi4Test {

    private static final String VICTORY_POINTS = "10";
    private static final String EXPANSION = MatchmakingOptions.POK_AND_TE_EXPANSION_OPTION;
    private static final String PACE = MatchmakingOptions.SLOWER_PACE_OPTION;
    // Confident sigma so 1v1 match quality is driven by the mean skill gap, as for calibrated players.
    private static final double CONFIDENT_SIGMA = 1.5;

    private final List<QueuedParty> parties = new ArrayList<>();
    private final Map<String, Rating> ratingsByUser = new HashMap<>();
    private long nextPartyId = 1;

    private MockedStatic<MatchmakingRatingEventService> ratingService;

    @BeforeEach
    void mockRatingService() {
        MatchmakingRatingEventService serviceMock = mock(MatchmakingRatingEventService.class);
        when(serviceMock.getPlayerRatings(any())).thenReturn(ratingsByUser);
        ratingService = Mockito.mockStatic(MatchmakingRatingEventService.class);
        ratingService.when(MatchmakingRatingEventService::get).thenReturn(serviceMock);
    }

    @AfterEach
    void closeRatingService() {
        ratingService.close();
    }

    @Test
    void formsOneGameFromThreeCompatibleSoloPlayers() {
        addSolo("p1");
        addSolo("p2");
        addSolo("p3");

        List<MatchedGame> games = MatchmakingGrouper.formGames(parties);

        assertThat(games).hasSize(1);
        assertThat(games.getFirst().playerCount()).isEqualTo("3");
        assertThat(games.getFirst().members())
                .extracting(MatchmakingQueueMember::getUserId)
                .containsExactlyInAnyOrder("p1", "p2", "p3");
    }

    @Test
    void prefersTheLargestSelectedPlayerCount() {
        // Four players who would accept either a 3- or 4-player game should be packed into a single 4-player game.
        for (String id : List.of("p1", "p2", "p3", "p4")) {
            addSolo(id, List.of("3", "4"));
        }

        List<MatchedGame> games = MatchmakingGrouper.formGames(parties);

        assertThat(games).hasSize(1);
        assertThat(games.getFirst().playerCount()).isEqualTo("4");
        assertThat(games.getFirst().members()).hasSize(4);
    }

    @Test
    void keepsAPartyTogetherInItsGame() {
        addParty(List.of("a1", "a2"));
        addSolo("s1");

        List<MatchedGame> games = MatchmakingGrouper.formGames(parties);

        assertThat(games).hasSize(1);
        assertThat(games.getFirst().members())
                .extracting(MatchmakingQueueMember::getUserId)
                .containsExactlyInAnyOrder("a1", "a2", "s1");
    }

    @Test
    void skipsWhenNoCombinationOfPartiesCompletesAGame() {
        // Two parties of two cannot be packed into a three-player game without splitting one.
        addParty(List.of("a1", "a2"));
        addParty(List.of("b1", "b2"));

        assertThat(MatchmakingGrouper.formGames(parties)).isEmpty();
    }

    @Test
    void formsNoGameWhenAPairIsIncompatible() {
        // p1 wants a TIGL game while the others do not, so no compatible trio can be assembled.
        addParty(List.of("p1"), List.of(MatchmakingOptions.TIGL_OPTION), List.of("3"), Duration.ZERO);
        addSolo("p2");
        addSolo("p3");

        assertThat(MatchmakingGrouper.formGames(parties)).isEmpty();
    }

    @Test
    void formsNearMatchWhenLongestQueuedHasWaitedEightHours() {
        // Two players want a 3p game but only two are available; one has waited the full 8h long-queue
        // duration (with a longer max queue time, so it is not yet near expiry).
        addParty(List.of("p1"), List.of(), List.of("3"), Duration.ofHours(8), "1 day");
        addParty(List.of("p2"), List.of(), List.of("3"), Duration.ZERO);

        List<MatchedGame> games = MatchmakingGrouper.formGames(parties);

        assertThat(games).hasSize(1);
        assertThat(games.getFirst().playerCount()).isEqualTo("3");
        assertThat(games.getFirst().members())
                .extracting(MatchmakingQueueMember::getUserId)
                .containsExactlyInAnyOrder("p1", "p2");
    }

    @Test
    void formsNearMatchWhenNearExpiryWithThreeOtherMatchedPlayers() {
        // A 5p game is one short; one player is within an hour of expiring (8h max) and three others matched.
        addParty(List.of("p1"), List.of(), List.of("5"), Duration.ofHours(7).plusMinutes(30));
        addParty(List.of("p2"), List.of(), List.of("5"), Duration.ZERO);
        addParty(List.of("p3"), List.of(), List.of("5"), Duration.ZERO);
        addParty(List.of("p4"), List.of(), List.of("5"), Duration.ZERO);

        List<MatchedGame> games = MatchmakingGrouper.formGames(parties);

        assertThat(games).hasSize(1);
        assertThat(games.getFirst().playerCount()).isEqualTo("5");
        assertThat(games.getFirst().members())
                .extracting(MatchmakingQueueMember::getUserId)
                .containsExactlyInAnyOrder("p1", "p2", "p3", "p4");
    }

    @Test
    void doesNotFormNearMatchWhenNearExpiryButTooFewOtherPlayers() {
        // A 3p game one short with a near-expiry player has only one other matched player, not the three required.
        addParty(List.of("p1"), List.of(), List.of("3"), Duration.ofHours(7).plusMinutes(30));
        addParty(List.of("p2"), List.of(), List.of("3"), Duration.ZERO);

        assertThat(MatchmakingGrouper.formGames(parties)).isEmpty();
    }

    @Test
    void doesNotFormNearMatchWhenNoOneIsNearExpiryOrLongQueued() {
        addParty(List.of("p1"), List.of(), List.of("3"), Duration.ZERO);
        addParty(List.of("p2"), List.of(), List.of("3"), Duration.ZERO);

        assertThat(MatchmakingGrouper.formGames(parties)).isEmpty();
    }

    @Test
    void realizesTheLargerNearMatchFirstAndSkipsOverlappingOnes() {
        // p1 (long-queued 8h) fits both a 4p and a 3p near match; the 4p one (more players) wins and consumes
        // p1, so the overlapping 3p near match with s1 is skipped.
        addParty(List.of("p1"), List.of(), List.of("3", "4"), Duration.ofHours(8), "1 day");
        addParty(List.of("q1"), List.of(), List.of("4"), Duration.ZERO);
        addParty(List.of("r1"), List.of(), List.of("4"), Duration.ZERO);
        addParty(List.of("s1"), List.of(), List.of("3"), Duration.ZERO);

        List<MatchedGame> games = MatchmakingGrouper.formGames(parties);

        assertThat(games).hasSize(1);
        assertThat(games.getFirst().playerCount()).isEqualTo("4");
        assertThat(games.getFirst().members())
                .extracting(MatchmakingQueueMember::getUserId)
                .containsExactlyInAnyOrder("p1", "q1", "r1");
    }

    private void addSolo(String userId) {
        addSolo(userId, List.of("3"));
    }

    private void addSolo(String userId, List<String> playerCounts) {
        addParty(List.of(userId), List.of(), playerCounts, Duration.ZERO);
    }

    private void addParty(List<String> userIds) {
        addParty(userIds, List.of(), List.of("3"), Duration.ZERO);
    }

    private void addParty(
            List<String> userIds, List<String> restrictions, List<String> playerCounts, Duration queueWait) {
        addParty(userIds, restrictions, playerCounts, queueWait, "8 hours");
    }

    private void addParty(
            List<String> userIds,
            List<String> restrictions,
            List<String> playerCounts,
            Duration queueWait,
            String maxQueueTime) {
        long partyId = nextPartyId;
        nextPartyId++;
        List<MatchmakingQueueMember> members = new ArrayList<>();
        for (String userId : userIds) {
            MatchmakingQueueMember member = new MatchmakingQueueMember();
            member.setUserId(userId);
            member.setPartyId(partyId);
            members.add(member);
        }
        MatchmakingQueueParty party = new MatchmakingQueueParty();
        party.setId(partyId);
        party.setQueued(true);
        party.setQueuedAt(Instant.now().minus(queueWait));
        party.setLeaderId(userIds.getFirst());
        parties.add(new QueuedParty(party, members, settings(playerCounts, restrictions, maxQueueTime)));
    }

    private void rate(String userId, double mean) {
        ratingsByUser.put(userId, new Rating(mean, CONFIDENT_SIGMA));
    }

    private static UserSettings settings(List<String> playerCounts, List<String> restrictions, String maxQueueTime) {
        UserSettings settings = new UserSettings();
        settings.setMatchmakingPlayerCounts(playerCounts);
        settings.setMatchmakingVictoryPointGoals(List.of(VICTORY_POINTS));
        settings.setMatchmakingExpansions(List.of(EXPANSION));
        settings.setMatchmakingPaces(List.of(PACE));
        settings.setMatchmakingRestrictions(restrictions);
        settings.setMatchmakingMaxQueueTime(maxQueueTime);
        return settings;
    }
}
