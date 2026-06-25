package ti4.spring.service.statistics.matchmaking.queue;

import static org.assertj.core.api.Assertions.assertThat;

import de.gesundkrank.jskills.Rating;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;

class MatchmakingCompatibilityServiceTest {

    private static final Set<Integer> ALL_BUCKETS = Set.of(0, 1, 2, 3, 4, 5);

    @Test
    void compatiblePlayersHaveNoReason() {
        assertThat(MatchmakingCompatibilityService.areIncompatible(
                        player("a").build(), player("b").build(), false))
                .isFalse();
    }

    @Test
    void avoidListBlocksInEitherDirection() {
        PlayerMatchmakingData a = player("a").avoidList("b").build();
        PlayerMatchmakingData b = player("b").build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b, false)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(b, a, false)).isTrue();
    }

    @Test
    void disagreeingOnTiglIsIncompatible() {
        PlayerMatchmakingData a =
                player("a").restrictions(MatchmakingOptions.TIGL_OPTION).build();
        PlayerMatchmakingData b = player("b").build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b, false)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(b, a, false)).isTrue();
    }

    @Test
    void onlyMatchFloatersBlocksNonFloatersEitherDirection() {
        PlayerMatchmakingData floater = player("a")
                .restrictions(MatchmakingOptions.ONLY_MATCH_FLOATERS_OPTION)
                .roleNames(MatchmakingOptions.FLOATERS_ROLE_NAME)
                .build();
        PlayerMatchmakingData nonFloater = player("b").build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(floater, nonFloater, false))
                .isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(nonFloater, floater, false))
                .isTrue();
    }

    @Test
    void onlyMatchWarriorsBlocksNonWarriors() {
        PlayerMatchmakingData warrior = player("a")
                .restrictions(MatchmakingOptions.ONLY_MATCH_WARRIORS_OPTION)
                .roleNames(MatchmakingOptions.WARRIORS_ROLE_NAME)
                .build();
        PlayerMatchmakingData nonWarrior = player("b").build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(warrior, nonWarrior, false))
                .isTrue();
    }

    @Test
    void avoidNewPlayersBlocksNewPlayerEitherDirection() {
        PlayerMatchmakingData veteran = player("a")
                .restrictions(MatchmakingOptions.AVOID_NEW_PLAYERS_OPTION)
                .build();
        PlayerMatchmakingData newcomer = player("b").completedGames(1).build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(veteran, newcomer, false))
                .isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(newcomer, veteran, false))
                .isTrue();
    }

    @Test
    void similarActiveHoursRequiresSharedBuckets() {
        PlayerMatchmakingData picky = player("a")
                .restrictions(MatchmakingOptions.SIMILAR_ACTIVE_HOURS_OPTION)
                .activeHourBuckets(0, 1, 2)
                .build();
        PlayerMatchmakingData fewShared = player("b").activeHourBuckets(0, 4, 5).build();
        PlayerMatchmakingData manyShared =
                player("c").activeHourBuckets(0, 1, 2, 5).build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(picky, fewShared, false))
                .isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(picky, manyShared, false))
                .isFalse();
    }

    @Test
    void similarSkillToleranceWidensWhenRelaxed() {
        // A 4.5-point skill gap (between confident ratings) yields a 1v1 match quality of ~0.73,
        // which fails the strict 0.80 gate but clears the relaxed 0.65 gate.
        PlayerMatchmakingData a = player("a")
                .restrictions(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION)
                .rating(20)
                .build();
        PlayerMatchmakingData b = player("b").rating(24.5).build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b, false)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b, true)).isFalse();
    }

    @Test
    void newPlayersAreComparedAtTheNewPlayerRating() {
        // 'a' is new, so its high stored rating is ignored in favour of the default new-player rating (20).
        PlayerMatchmakingData a = player("a")
                .restrictions(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION)
                .rating(50)
                .completedGames(1)
                .build();
        PlayerMatchmakingData b = player("b").rating(21).build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b, false)).isFalse();
    }

    private static Builder player(String userId) {
        return new Builder(userId);
    }

    /** A small fluent builder so each test only states the attributes it cares about. */
    private static final class Builder {
        // Confident sigma so 1v1 match quality is driven by the mean skill gap, as for calibrated players.
        private static final double CONFIDENT_SIGMA = 1.5;

        private final String userId;
        private List<String> restrictions = List.of();
        private List<String> avoidList = List.of();
        private Rating rating = new Rating(25, CONFIDENT_SIGMA);
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
            rating = new Rating(value, CONFIDENT_SIGMA);
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

        private PlayerMatchmakingData build() {
            return new PlayerMatchmakingData(
                    userId, restrictions, avoidList, rating, activeHourBuckets, completedGames, roleNames, false);
        }
    }
}
