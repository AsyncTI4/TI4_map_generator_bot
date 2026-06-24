package ti4.spring.service.statistics.matchmaking.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.settings.users.UserSettings;

class MatchmakingGrouperTest {

    private static final String VICTORY_POINTS = "10";
    private static final String EXPANSION = MatchmakingOptions.POK_AND_TE_EXPANSION_OPTION;
    private static final String PACE = MatchmakingOptions.NO_PACE_OPTION;

    private final MatchmakingGrouper grouper = new MatchmakingGrouper();
    private final Map<MatchmakingQueueMember, PlayerMatchData> matchData = new HashMap<>();
    private final List<QueuedParty> parties = new ArrayList<>();
    private long nextPartyId = 1;

    @Test
    void formsOneGameFromThreeCompatibleSoloPlayers() {
        addSolo("p1");
        addSolo("p2");
        addSolo("p3");

        List<MatchedGame> games = grouper.formGames(parties, matchData);

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
            addSolo(id, defaultData(id), List.of("3", "4"));
        }

        List<MatchedGame> games = grouper.formGames(parties, matchData);

        assertThat(games).hasSize(1);
        assertThat(games.getFirst().playerCount()).isEqualTo("4");
        assertThat(games.getFirst().members()).hasSize(4);
    }

    @Test
    void keepsAPartyTogetherInItsGame() {
        addParty(List.of("a1", "a2"));
        addSolo("s1");

        List<MatchedGame> games = grouper.formGames(parties, matchData);

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

        assertThat(grouper.formGames(parties, matchData)).isEmpty();
    }

    @Test
    void formsNoGameWhenAPairIsIncompatible() {
        addSolo("p1", defaultData("p1").withAvoid("p2"), List.of("3"));
        addSolo("p2");
        addSolo("p3");

        assertThat(grouper.formGames(parties, matchData)).isEmpty();
    }

    @Test
    void halfQueueTimePassedRelaxesSkillMatching() {
        List<String> skill = List.of(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION);

        // Ratings 20/23/23: the 20-vs-23 gap exceeds the strict tolerance (2) but is within the relaxed one (4).
        addSolo("a", new Data("a", skill, 20, false), List.of("3"));
        addSolo("b", new Data("b", skill, 23, false), List.of("3"));
        addSolo("c", new Data("c", skill, 23, false), List.of("3"));
        assertThat(grouper.formGames(parties, matchData)).isEmpty();

        parties.clear();
        matchData.clear();
        addSolo("a", new Data("a", skill, 20, true), List.of("3"));
        addSolo("b", new Data("b", skill, 23, true), List.of("3"));
        addSolo("c", new Data("c", skill, 23, true), List.of("3"));
        assertThat(grouper.formGames(parties, matchData)).hasSize(1);
    }

    private void addSolo(String userId) {
        addSolo(userId, defaultData(userId), List.of("3"));
    }

    private void addSolo(String userId, Data data, List<String> playerCounts) {
        addParty(List.of(userId), List.of(data), playerCounts);
    }

    private void addParty(List<String> userIds) {
        addParty(userIds, userIds.stream().map(this::defaultData).toList(), List.of("3"));
    }

    private void addParty(List<String> userIds, List<Data> datas, List<String> playerCounts) {
        long partyId = nextPartyId;
        nextPartyId++;
        List<MatchmakingQueueMember> members = new ArrayList<>();
        for (int i = 0; i < userIds.size(); i++) {
            MatchmakingQueueMember member = new MatchmakingQueueMember();
            member.setUserId(userIds.get(i));
            member.setPartyId(partyId);
            members.add(member);
            matchData.put(member, datas.get(i).toPlayerMatchData());
        }
        MatchmakingQueueParty party = new MatchmakingQueueParty();
        party.setId(partyId);
        party.setQueued(true);
        party.setQueuedAt(Instant.EPOCH.plusSeconds(partyId));
        party.setLeaderId(userIds.getFirst());
        parties.add(new QueuedParty(party, members, settings(playerCounts)));
    }

    private Data defaultData(String userId) {
        return new Data(userId, List.of(), 25, false);
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

    /** Test fixture for one player's match data; defaults to a veteran with no restrictions or avoid list. */
    private static final class Data {
        private final String userId;
        private final List<String> restrictions;
        private final double rating;
        private final boolean halfQueueTimePassed;
        private List<String> avoidList = List.of();

        private Data(String userId, List<String> restrictions, double rating, boolean halfQueueTimePassed) {
            this.userId = userId;
            this.restrictions = restrictions;
            this.rating = rating;
            this.halfQueueTimePassed = halfQueueTimePassed;
        }

        private Data withAvoid(String avoidedUserId) {
            avoidList = List.of(avoidedUserId);
            return this;
        }

        private PlayerMatchData toPlayerMatchData() {
            return new PlayerMatchData(
                    userId,
                    restrictions,
                    avoidList,
                    BigDecimal.valueOf(rating),
                    Set.of(0, 1, 2, 3, 4, 5),
                    5,
                    Set.of(),
                    halfQueueTimePassed);
        }
    }
}
