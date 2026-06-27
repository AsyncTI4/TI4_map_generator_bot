package ti4.spring.service.statistics.matchmaking.queue;

import static org.assertj.core.api.Assertions.assertThat;

import de.gesundkrank.jskills.Rating;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;

class MatchmakingCompatibilityServiceTest {

    private static final Set<Integer> ALL_BUCKETS = Set.of(0, 1, 2, 3, 4, 5);

    @Test
    void compatiblePlayersHaveNoReason() {
        assertThat(MatchmakingCompatibilityService.areIncompatible(
                        player("a").build(), player("b").build()))
                .isFalse();
    }

    @Test
    void avoidListBlocksInEitherDirection() {
        PlayerMatchmakingData a = player("a").avoidList("b").build();
        PlayerMatchmakingData b = player("b").build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(b, a)).isTrue();
    }

    @Test
    void disagreeingOnTiglIsIncompatible() {
        PlayerMatchmakingData a =
                player("a").restrictions(MatchmakingOptions.TIGL_OPTION).build();
        PlayerMatchmakingData b = player("b").build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(b, a)).isTrue();
    }

    @Test
    void tiglPlayersOfDifferentRankAreIncompatibleInStandardGame() {
        PlayerMatchmakingData hero = player("a")
                .restrictions(MatchmakingOptions.TIGL_OPTION)
                .tiglRank("Hero")
                .build();
        PlayerMatchmakingData agent = player("b")
                .restrictions(MatchmakingOptions.TIGL_OPTION)
                .tiglRank("Agent")
                .build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(
                        hero, agent, MatchmakingOptions.POK_AND_TE_EXPANSION_OPTION))
                .isTrue();
    }

    @Test
    void tiglPlayersOfSameRankAreCompatibleInStandardGame() {
        PlayerMatchmakingData a = player("a")
                .restrictions(MatchmakingOptions.TIGL_OPTION)
                .tiglRank("Hero")
                .build();
        PlayerMatchmakingData b = player("b")
                .restrictions(MatchmakingOptions.TIGL_OPTION)
                .tiglRank("Hero")
                .build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(
                        a, b, MatchmakingOptions.POK_AND_TE_EXPANSION_OPTION))
                .isFalse();
    }

    @Test
    void nonStandardGamesMatchOnFracturedRank() {
        // Same standard rank but different Fractured rank: incompatible for a Franken (non-standard) game.
        PlayerMatchmakingData a = player("a")
                .restrictions(MatchmakingOptions.TIGL_OPTION)
                .tiglRank("Hero")
                .tiglFracturedRank("Archon")
                .build();
        PlayerMatchmakingData b = player("b")
                .restrictions(MatchmakingOptions.TIGL_OPTION)
                .tiglRank("Hero")
                .tiglFracturedRank("Thrall")
                .build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b, MatchmakingOptions.FRANKEN_EXPANSION_OPTION))
                .isTrue();
        // The standard ranks match, so a standard game pairs them fine.
        assertThat(MatchmakingCompatibilityService.areIncompatible(
                        a, b, MatchmakingOptions.POK_AND_TE_EXPANSION_OPTION))
                .isFalse();
    }

    @Test
    void rankIsIgnoredWhenTiglNotChosen() {
        PlayerMatchmakingData a = player("a").tiglRank("Hero").build();
        PlayerMatchmakingData b = player("b").tiglRank("Agent").build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(
                        a, b, MatchmakingOptions.POK_AND_TE_EXPANSION_OPTION))
                .isFalse();
    }

    @Test
    void rankIsIgnoredWhenNoExpansionContext() {
        PlayerMatchmakingData hero = player("a")
                .restrictions(MatchmakingOptions.TIGL_OPTION)
                .tiglRank("Hero")
                .build();
        PlayerMatchmakingData agent = player("b")
                .restrictions(MatchmakingOptions.TIGL_OPTION)
                .tiglRank("Agent")
                .build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(hero, agent)).isFalse();
    }

    @Test
    void onlyMatchFloatersBlocksNonFloatersEitherDirection() {
        PlayerMatchmakingData floater = player("a")
                .restrictions(MatchmakingOptions.ONLY_MATCH_FLOATERS_OPTION)
                .roleNames(MatchmakingOptions.FLOATERS_ROLE_NAME)
                .build();
        PlayerMatchmakingData nonFloater = player("b").build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(floater, nonFloater))
                .isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(nonFloater, floater))
                .isTrue();
    }

    @Test
    void onlyMatchWarriorsBlocksNonWarriors() {
        PlayerMatchmakingData warrior = player("a")
                .restrictions(MatchmakingOptions.ONLY_MATCH_WARRIORS_OPTION)
                .roleNames(MatchmakingOptions.WARRIORS_ROLE_NAME)
                .build();
        PlayerMatchmakingData nonWarrior = player("b").build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(warrior, nonWarrior))
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

        assertThat(MatchmakingCompatibilityService.areIncompatible(picky, fewShared))
                .isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(picky, manyShared))
                .isFalse();
    }

    @Test
    void getSimilarSkillThresholdDecaysWithQueueTime() {
        // A 6-point skill gap (between confident ratings) yields a 1v1 match quality of ~0.59, which fails the
        // starting 0.70 threshold. Only 'a' asks for similar skill, so only 'a's threshold is checked.
        PlayerMatchmakingData freshlyQueued = player("a")
                .restrictions(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION)
                .rating(20)
                .build();
        PlayerMatchmakingData b = player("b").rating(26).build();
        assertThat(MatchmakingCompatibilityService.areIncompatible(freshlyQueued, b))
                .isTrue();

        // After an hour 'a's threshold has decayed two steps (0.70 -> 0.50), which the ~0.59 quality clears.
        PlayerMatchmakingData patient = player("a")
                .restrictions(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION)
                .rating(20)
                .queueWait(Duration.ofMinutes(60))
                .build();
        assertThat(MatchmakingCompatibilityService.areIncompatible(patient, b)).isFalse();
    }

    @Test
    void bothDirectionsMustClearWhenBothWantSimilarSkill() {
        // 'a' has waited to the 0.40 floor and accepts the ~0.59 gap, but 'b' just queued at the 0.70 threshold
        // and still wants similar skill, so the stricter direction blocks the match.
        PlayerMatchmakingData patientA = player("a")
                .restrictions(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION)
                .rating(20)
                .queueWait(Duration.ofMinutes(90))
                .build();
        PlayerMatchmakingData freshB = player("b")
                .restrictions(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION)
                .rating(26)
                .build();
        assertThat(MatchmakingCompatibilityService.areIncompatible(patientA, freshB))
                .isTrue();

        // Once 'b' has also waited out its threshold, both directions clear the floor.
        PlayerMatchmakingData patientB = player("b")
                .restrictions(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION)
                .rating(26)
                .queueWait(Duration.ofMinutes(90))
                .build();
        assertThat(MatchmakingCompatibilityService.areIncompatible(patientA, patientB))
                .isFalse();
    }

    @Test
    void skillGapIsIgnoredWhenNeitherWantsSimilarSkill() {
        PlayerMatchmakingData a = player("a").rating(20).build();
        PlayerMatchmakingData b = player("b").rating(40).build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isFalse();
    }

    private static Builder player(String userId) {
        return new Builder(userId);
    }

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
        private Duration queueWait = Duration.ZERO;
        private String tiglRank = MatchmakingOptions.UNRANKED_OPTION;
        private String tiglFracturedRank = MatchmakingOptions.UNRANKED_OPTION;

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

        private Builder queueWait(Duration value) {
            queueWait = value;
            return this;
        }

        private Builder tiglRank(String value) {
            tiglRank = value;
            return this;
        }

        private Builder tiglFracturedRank(String value) {
            tiglFracturedRank = value;
            return this;
        }

        private PlayerMatchmakingData build() {
            return new PlayerMatchmakingData(
                    userId,
                    restrictions,
                    avoidList,
                    rating,
                    activeHourBuckets,
                    completedGames,
                    roleNames,
                    queueWait,
                    tiglRank,
                    tiglFracturedRank);
        }
    }
}
