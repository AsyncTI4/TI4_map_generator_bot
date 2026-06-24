package ti4.spring.service.statistics.matchmaking.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;

class MatchmakingCompatibilityServiceTest {

    private static final Set<Integer> ALL_BUCKETS = Set.of(0, 1, 2, 3, 4, 5);

    @Test
    void compatiblePlayersHaveNoReason() {
        assertThat(MatchmakingCompatibilityService.incompatibilityReason(
                        player("a").build(), player("b").build(), false))
                .isEmpty();
    }

    @Test
    void avoidListBlocksInEitherDirection() {
        PlayerMatchData a = player("a").avoidList("b").build();
        PlayerMatchData b = player("b").build();

        assertThat(MatchmakingCompatibilityService.incompatibilityReason(a, b, false))
                .isPresent();
        assertThat(MatchmakingCompatibilityService.incompatibilityReason(b, a, false))
                .isPresent();
    }

    @Test
    void disagreeingOnTiglIsIncompatible() {
        PlayerMatchData a =
                player("a").restrictions(MatchmakingOptions.TIGL_OPTION).build();
        PlayerMatchData b = player("b").build();

        assertThat(MatchmakingCompatibilityService.incompatibilityReason(a, b, false))
                .isPresent();
        assertThat(MatchmakingCompatibilityService.incompatibilityReason(b, a, false))
                .isPresent();
    }

    @Test
    void onlyMatchFloatersBlocksNonFloatersEitherDirection() {
        PlayerMatchData floater = player("a")
                .restrictions(MatchmakingOptions.ONLY_MATCH_FLOATERS_OPTION)
                .roleNames(MatchmakingOptions.FLOATERS_ROLE_NAME)
                .build();
        PlayerMatchData nonFloater = player("b").build();

        assertThat(MatchmakingCompatibilityService.incompatibilityReason(floater, nonFloater, false))
                .isPresent();
        assertThat(MatchmakingCompatibilityService.incompatibilityReason(nonFloater, floater, false))
                .isPresent();
    }

    @Test
    void onlyMatchWarriorsBlocksNonWarriors() {
        PlayerMatchData warrior = player("a")
                .restrictions(MatchmakingOptions.ONLY_MATCH_WARRIORS_OPTION)
                .roleNames(MatchmakingOptions.WARRIORS_ROLE_NAME)
                .build();
        PlayerMatchData nonWarrior = player("b").build();

        assertThat(MatchmakingCompatibilityService.incompatibilityReason(warrior, nonWarrior, false))
                .isPresent();
    }

    @Test
    void avoidNewPlayersBlocksNewPlayerEitherDirection() {
        PlayerMatchData veteran = player("a")
                .restrictions(MatchmakingOptions.AVOID_NEW_PLAYERS_OPTION)
                .build();
        PlayerMatchData newcomer = player("b").completedGames(1).build();

        assertThat(MatchmakingCompatibilityService.incompatibilityReason(veteran, newcomer, false))
                .isPresent();
        assertThat(MatchmakingCompatibilityService.incompatibilityReason(newcomer, veteran, false))
                .isPresent();
    }

    @Test
    void similarActiveHoursRequiresSharedBuckets() {
        PlayerMatchData picky = player("a")
                .restrictions(MatchmakingOptions.SIMILAR_ACTIVE_HOURS_OPTION)
                .activeHourBuckets(0, 1, 2)
                .build();
        PlayerMatchData fewShared = player("b").activeHourBuckets(0, 4, 5).build();
        PlayerMatchData manyShared = player("c").activeHourBuckets(0, 1, 2, 5).build();

        assertThat(MatchmakingCompatibilityService.incompatibilityReason(picky, fewShared, false))
                .isPresent();
        assertThat(MatchmakingCompatibilityService.incompatibilityReason(picky, manyShared, false))
                .isEmpty();
    }

    @Test
    void similarSkillToleranceWidensWhenRelaxed() {
        PlayerMatchData a = player("a")
                .restrictions(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION)
                .rating(20)
                .build();
        PlayerMatchData b = player("b").rating(23).build();

        assertThat(MatchmakingCompatibilityService.incompatibilityReason(a, b, false))
                .isPresent();
        assertThat(MatchmakingCompatibilityService.incompatibilityReason(a, b, true))
                .isEmpty();
    }

    @Test
    void newPlayersAreComparedAtTheNewPlayerRating() {
        // 'a' is new, so its high stored rating is ignored in favour of the default new-player rating (20).
        PlayerMatchData a = player("a")
                .restrictions(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION)
                .rating(50)
                .completedGames(1)
                .build();
        PlayerMatchData b = player("b").rating(21).build();

        assertThat(MatchmakingCompatibilityService.incompatibilityReason(a, b, false))
                .isEmpty();
    }

    private static Builder player(String userId) {
        return new Builder(userId);
    }

    /** A small fluent builder so each test only states the attributes it cares about. */
    private static final class Builder {
        private final String userId;
        private List<String> restrictions = List.of();
        private List<String> avoidList = List.of();
        private BigDecimal rating = BigDecimal.valueOf(25);
        private Set<Integer> activeHourBuckets = ALL_BUCKETS;
        private int completedGames = 5;
        private Set<String> roleNames = Set.of();

        private Builder(String userId) {
            this.userId = userId;
        }

        private Builder restrictions(String... values) {
            restrictions = List.of(values);
            return this;
        }

        private Builder avoidList(String... values) {
            avoidList = List.of(values);
            return this;
        }

        private Builder rating(double value) {
            rating = BigDecimal.valueOf(value);
            return this;
        }

        private Builder activeHourBuckets(Integer... values) {
            activeHourBuckets = Set.of(values);
            return this;
        }

        private Builder completedGames(int value) {
            completedGames = value;
            return this;
        }

        private Builder roleNames(String... values) {
            roleNames = Set.of(values);
            return this;
        }

        private PlayerMatchData build() {
            return new PlayerMatchData(
                    userId, restrictions, avoidList, rating, activeHourBuckets, completedGames, roleNames, false);
        }
    }
}
