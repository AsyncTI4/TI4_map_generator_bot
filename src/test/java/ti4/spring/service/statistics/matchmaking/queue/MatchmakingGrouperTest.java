package ti4.spring.service.statistics.matchmaking.queue;

import static org.assertj.core.api.Assertions.assertThat;

import de.gesundkrank.jskills.Rating;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.settings.users.UserSettings;

class MatchmakingGrouperTest {

    private static final String VICTORY_POINTS = "10";
    private static final String EXPANSION = MatchmakingOptions.POK_AND_TE_EXPANSION_OPTION;
    private static final String PACE = MatchmakingOptions.SLOWER_PACE_OPTION;

    private final List<QueuedParty> parties = new ArrayList<>();
    private long nextPartyId = 1;

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
        addSolo("p1", List.of("3"));
        addSolo("p2");
        addSolo("p3");

        assertThat(MatchmakingGrouper.formGames(parties)).isEmpty();
    }

    @Test
    void queueTimeDecaysSkillMatching() {
        List<String> skill = List.of(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION);

        // Ratings 20/26/26: the 20-vs-26 skill gap gives a 1v1 match quality (~0.59) that fails the starting
        // 0.70 threshold but clears the 0.40 floor, so the trio only forms once the threshold has decayed.
        addSolo("a", List.of("3"));
        addSolo("b", List.of("3"));
        addSolo("c", List.of("3"));
        assertThat(MatchmakingGrouper.formGames(parties)).isEmpty();

        parties.clear();
        // After 90 minutes the threshold has decayed three steps (0.70 -> 0.40), low enough to accept the gap.
        addSolo("a", List.of("3"));
        addSolo("b", List.of("3"));
        addSolo("c", List.of("3"));
        assertThat(MatchmakingGrouper.formGames(parties)).hasSize(1);
    }

    private void addSolo(String userId) {
        addSolo(userId, List.of("3"));
    }

    private void addSolo(String userId, List<String> playerCounts) {
        addParty(List.of(userId), playerCounts);
    }

    private void addParty(List<String> userIds) {
        addParty(userIds, List.of("3"));
    }

    private void addParty(List<String> userIds, List<String> playerCounts) {
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
        party.setQueuedAt(Instant.EPOCH.plusSeconds(partyId));
        party.setLeaderId(userIds.getFirst());
        parties.add(new QueuedParty(party, members, settings(playerCounts)));
    }

    private static UserSettings settings(List<String> playerCounts) {
        UserSettings settings = new UserSettings();
        settings.setMatchmakingPlayerCounts(playerCounts);
        settings.setMatchmakingVictoryPointGoals(List.of(VICTORY_POINTS));
        settings.setMatchmakingExpansions(List.of(EXPANSION));
        settings.setMatchmakingPaces(List.of(PACE));
        settings.setMatchmakingRestrictions(List.of());
        settings.setMatchmakingMaxQueueTime("8 hours");
        return settings;
    }

    private static final class Data {
        private final String userId;
        private final List<String> restrictions;
        private final double rating;
        private final Duration queueWait;
        private List<String> avoidList = List.of();

        private Data(String userId, List<String> restrictions, double rating, Duration queueWait) {
            this.userId = userId;
            this.restrictions = restrictions;
            this.rating = rating;
            this.queueWait = queueWait;
        }

        private Data withAvoid(String avoidedUserId) {
            avoidList = List.of(avoidedUserId);
            return this;
        }

        private PlayerMatchmakingData toPlayerMatchData() {
            return new PlayerMatchmakingData(
                    userId,
                    restrictions,
                    avoidList,
                    new Rating(rating, 1.5),
                    Set.of(0, 1, 2, 3, 4, 5),
                    5,
                    Set.of(),
                    queueWait);
        }
    }
}
