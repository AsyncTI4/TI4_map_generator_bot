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
    void largeSkillGapBlocksFreshlyQueuedPlayers() {
        // Fresh queue: the window is the 4-point starting threshold, so a 20-point gap is too large.
        PlayerMatchmakingData a = player("a").rating(20).build();
        PlayerMatchmakingData b = player("b").rating(40).build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isTrue();
    }

    @Test
    void smallSkillGapIsAllowed() {
        PlayerMatchmakingData a = player("a").rating(24).build();
        PlayerMatchmakingData b = player("b").rating(26).build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isFalse();
    }

    @Test
    void skillWindowWidensWithQueueTime() {
        // A 6-point gap exceeds the fresh 4-point window, but after 4 hours the window widens to 6 (4 + 2 steps).
        PlayerMatchmakingData fresh = player("a").rating(20).build();
        PlayerMatchmakingData b = player("b").rating(26).build();
        assertThat(MatchmakingCompatibilityService.areIncompatible(fresh, b)).isTrue();

        PlayerMatchmakingData patient =
                player("a").rating(20).queueWait(Duration.ofHours(4)).build();
        assertThat(MatchmakingCompatibilityService.areIncompatible(patient, b)).isFalse();
    }

    @Test
    void floaterAndWarriorAreKeptApartWhileQueueIsShort() {
        PlayerMatchmakingData floater =
                player("a").roleNames(MatchmakingOptions.FLOATERS_ROLE_NAME).build();
        PlayerMatchmakingData warrior =
                player("b").roleNames(MatchmakingOptions.WARRIORS_ROLE_NAME).build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(floater, warrior))
                .isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(warrior, floater))
                .isTrue();
    }

    @Test
    void floaterAndWarriorMatchAfterEightHourWait() {
        PlayerMatchmakingData floater = player("a")
                .roleNames(MatchmakingOptions.FLOATERS_ROLE_NAME)
                .queueWait(Duration.ofHours(8))
                .build();
        PlayerMatchmakingData warrior =
                player("b").roleNames(MatchmakingOptions.WARRIORS_ROLE_NAME).build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(floater, warrior))
                .isFalse();
    }

    @Test
    void twoFloatersAreNotKeptApart() {
        PlayerMatchmakingData a =
                player("a").roleNames(MatchmakingOptions.FLOATERS_ROLE_NAME).build();
        PlayerMatchmakingData b =
                player("b").roleNames(MatchmakingOptions.FLOATERS_ROLE_NAME).build();

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
