package ti4.spring.service.statistics.matchmaking.queue;

import static org.assertj.core.api.Assertions.assertThat;

import de.gesundkrank.jskills.Rating;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;

class MatchmakingCompatibilityServiceTest {

    private static final Set<Integer> ALL_HOURS =
            Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23);

    // Typical evening-hours profiles per region, expressed in UTC (12 contiguous hot hours each).
    // EU (Central, UTC+1): 13:00–01:00 local -> 12–23 UTC
    private static final Set<Integer> EU_HOURS = Set.of(12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23);
    // USA (Eastern, UTC-5): 17:00–05:00 local -> 22,23,0..9 UTC
    private static final Set<Integer> USA_HOURS = Set.of(22, 23, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    // APAC (Japan, UTC+9): 17:00–05:00 local -> 8..19 UTC
    private static final Set<Integer> APAC_HOURS = Set.of(8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19);
    // SEA (Singapore/Bangkok, UTC+7/+8): 17:00–05:00 local -> 10..21 UTC
    private static final Set<Integer> SEA_HOURS = Set.of(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);

    // Full-day (16 awake / 8 asleep) profiles per region, expressed in UTC.
    // Awake 06:00–22:00 local time -> 16 hot hours; the 8 asleep hours span local 22:00–06:00.
    // EU (Central, UTC+1) awake -> UTC 05..20
    private static final Set<Integer> EU_16H_HOURS = Set.of(5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
    // USA (Eastern, UTC-5) awake -> UTC 11..23,0,1,2
    private static final Set<Integer> USA_16H_HOURS =
            Set.of(11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 0, 1, 2);
    // APAC (Japan, UTC+9) awake -> UTC 21,22,23,0..12
    private static final Set<Integer> APAC_16H_HOURS = Set.of(21, 22, 23, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    // SEA (Singapore, UTC+8) awake -> UTC 22,23,0..13
    private static final Set<Integer> SEA_16H_HOURS = Set.of(22, 23, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);

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
        PlayerMatchmakingData a = player("a").tigl(true).tiglRanks("Hero").build();
        PlayerMatchmakingData b = player("b").build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(b, a)).isTrue();
    }

    @Test
    void tiglPlayersWithDisjointRanksAreIncompatible() {
        PlayerMatchmakingData hero = player("a").tigl(true).tiglRanks("Hero").build();
        PlayerMatchmakingData agent = player("b").tigl(true).tiglRanks("Agent").build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(hero, agent)).isTrue();
    }

    @Test
    void tiglPlayersSharingARankAreCompatible() {
        PlayerMatchmakingData a =
                player("a").tigl(true).tiglRanks("Hero", "Commander").build();
        PlayerMatchmakingData b =
                player("b").tigl(true).tiglRanks("Commander", "Agent").build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isFalse();
    }

    @Test
    void tiglGamesAreNotBlockedBySkillGap() {
        // Both TIGL and sharing a rank: a wide skill gap that would block a normal match is ignored.
        PlayerMatchmakingData a =
                player("a").tigl(true).tiglRanks("Hero").rating(20).build();
        PlayerMatchmakingData b =
                player("b").tigl(true).tiglRanks("Hero").rating(40).build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isFalse();
    }

    @Test
    void rankIsIgnoredWhenTiglNotChosen() {
        PlayerMatchmakingData a = player("a").tiglRanks("Hero").build();
        PlayerMatchmakingData b = player("b").tiglRanks("Agent").build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isFalse();
    }

    @Test
    void strictActiveHoursRequiresTwelveSharedHours() {
        PlayerMatchmakingData picky = player("a")
                .restrictions(MatchmakingOptions.STRICT_SIMILAR_ACTIVE_HOURS_OPTION)
                .activeHours(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
                .build();
        PlayerMatchmakingData elevenShared = player("b")
                .activeHours(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 21, 22, 23)
                .build();
        PlayerMatchmakingData twelveShared = player("c")
                .activeHours(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 21, 22, 23)
                .build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(picky, elevenShared))
                .isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(picky, twelveShared))
                .isFalse();
    }

    @Test
    void looseActiveHoursRequiresTenSharedHours() {
        PlayerMatchmakingData picky = player("a")
                .restrictions(MatchmakingOptions.LOOSE_SIMILAR_ACTIVE_HOURS_OPTION)
                .activeHours(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
                .build();
        PlayerMatchmakingData nineShared = player("b")
                .activeHours(0, 1, 2, 3, 4, 5, 6, 7, 8, 20, 21, 22, 23)
                .build();
        PlayerMatchmakingData tenShared = player("c")
                .activeHours(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 20, 21, 22, 23)
                .build();

        assertThat(MatchmakingCompatibilityService.areIncompatible(picky, nineShared))
                .isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(picky, tenShared))
                .isFalse();
    }

    @Test
    void theStricterOfTheTwoLevelsApplies() {
        PlayerMatchmakingData strict = player("a")
                .restrictions(MatchmakingOptions.STRICT_SIMILAR_ACTIVE_HOURS_OPTION)
                .activeHours(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
                .build();
        PlayerMatchmakingData loose = player("b")
                .restrictions(MatchmakingOptions.LOOSE_SIMILAR_ACTIVE_HOURS_OPTION)
                .activeHours(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 21, 22, 23)
                .build();

        // 11 shared hours clears the loose level but not the strict one.
        assertThat(MatchmakingCompatibilityService.areIncompatible(strict, loose))
                .isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(loose, strict))
                .isTrue();
    }

    @Test
    void playerWithTooFewActiveHoursCanNeverMatch() {
        PlayerMatchmakingData thirteenHours = player("a")
                .activeHours(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
                .build();

        // Strict needs 12 shared hours out of at least 14 on record; loose needs 10 out of 12.
        assertThat(MatchmakingCompatibilityService.hasEnoughActiveHourDataToMatch(
                        thirteenHours, MatchmakingOptions.STRICT_SIMILAR_ACTIVE_HOURS_REQUIREMENT))
                .isFalse();
        assertThat(MatchmakingCompatibilityService.hasEnoughActiveHourDataToMatch(
                        thirteenHours, MatchmakingOptions.LOOSE_SIMILAR_ACTIVE_HOURS_REQUIREMENT))
                .isTrue();
    }

    @Test
    void playerWithEnoughActiveHoursCanMatch() {
        assertThat(MatchmakingCompatibilityService.hasEnoughActiveHourDataToMatch(
                        player("a")
                                .activeHours(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
                                .build(),
                        MatchmakingOptions.STRICT_SIMILAR_ACTIVE_HOURS_REQUIREMENT))
                .isTrue();
    }

    @Test
    void euAndUsaPlayersAreNotMatched() {
        PlayerMatchmakingData eu = regional("eu", EU_HOURS);
        PlayerMatchmakingData usa = regional("usa", USA_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(eu, usa)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(usa, eu)).isTrue();
    }

    @Test
    void euAndApacPlayersAreNotMatched() {
        PlayerMatchmakingData eu = regional("eu", EU_HOURS);
        PlayerMatchmakingData apac = regional("apac", APAC_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(eu, apac)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(apac, eu)).isTrue();
    }

    @Test
    void euAndSeaPlayersAreMatchedOnTheLooseLevel() {
        // Their evening windows overlap for exactly 10 hours: enough for loose, short of strict.
        PlayerMatchmakingData eu = regional("eu", EU_HOURS);
        PlayerMatchmakingData sea = regional("sea", SEA_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(eu, sea)).isFalse();
        assertThat(MatchmakingCompatibilityService.areIncompatible(sea, eu)).isFalse();
    }

    @Test
    void euAndSeaPlayersAreNotMatchedOnTheStrictLevel() {
        PlayerMatchmakingData eu = regional("eu", EU_HOURS, MatchmakingOptions.STRICT_SIMILAR_ACTIVE_HOURS_OPTION);
        PlayerMatchmakingData sea = regional("sea", SEA_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(eu, sea)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(sea, eu)).isTrue();
    }

    @Test
    void usaAndApacPlayersAreNotMatched() {
        PlayerMatchmakingData usa = regional("usa", USA_HOURS);
        PlayerMatchmakingData apac = regional("apac", APAC_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(usa, apac)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(apac, usa)).isTrue();
    }

    @Test
    void usaAndSeaPlayersAreNotMatched() {
        PlayerMatchmakingData usa = regional("usa", USA_HOURS);
        PlayerMatchmakingData sea = regional("sea", SEA_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(usa, sea)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(sea, usa)).isTrue();
    }

    @Test
    void apacAndSeaPlayersAreMatchedOnTheLooseLevel() {
        // One timezone apart: 10 shared hours, which the loose level accepts.
        PlayerMatchmakingData apac = regional("apac", APAC_HOURS);
        PlayerMatchmakingData sea = regional("sea", SEA_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(apac, sea)).isFalse();
        assertThat(MatchmakingCompatibilityService.areIncompatible(sea, apac)).isFalse();
    }

    @Test
    void apacAndSeaPlayersAreNotMatchedOnTheStrictLevel() {
        PlayerMatchmakingData apac =
                regional("apac", APAC_HOURS, MatchmakingOptions.STRICT_SIMILAR_ACTIVE_HOURS_OPTION);
        PlayerMatchmakingData sea = regional("sea", SEA_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(apac, sea)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(sea, apac)).isTrue();
    }

    @Test
    void twoEuPlayersMatch() {
        PlayerMatchmakingData a = regional("eu1", EU_HOURS);
        PlayerMatchmakingData b = regional("eu2", EU_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isFalse();
    }

    @Test
    void twoUsaPlayersMatch() {
        PlayerMatchmakingData a = regional("usa1", USA_HOURS);
        PlayerMatchmakingData b = regional("usa2", USA_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isFalse();
    }

    @Test
    void twoApacPlayersMatch() {
        PlayerMatchmakingData a = regional("apac1", APAC_HOURS);
        PlayerMatchmakingData b = regional("apac2", APAC_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isFalse();
    }

    @Test
    void twoSeaPlayersMatch() {
        PlayerMatchmakingData a = regional("sea1", SEA_HOURS);
        PlayerMatchmakingData b = regional("sea2", SEA_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isFalse();
    }

    @Test
    void euAndUsaAwakeSixteenHoursAreNotMatched() {
        PlayerMatchmakingData eu = regional16h("eu", EU_16H_HOURS);
        PlayerMatchmakingData usa = regional16h("usa", USA_16H_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(eu, usa)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(usa, eu)).isTrue();
    }

    @Test
    void euAndApacAwakeSixteenHoursAreNotMatched() {
        PlayerMatchmakingData eu = regional16h("eu", EU_16H_HOURS);
        PlayerMatchmakingData apac = regional16h("apac", APAC_16H_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(eu, apac)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(apac, eu)).isTrue();
    }

    @Test
    void euAndSeaAwakeSixteenHoursAreNotMatched() {
        PlayerMatchmakingData eu = regional16h("eu", EU_16H_HOURS);
        PlayerMatchmakingData sea = regional16h("sea", SEA_16H_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(eu, sea)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(sea, eu)).isTrue();
    }

    @Test
    void usaAndApacAwakeSixteenHoursAreNotMatched() {
        PlayerMatchmakingData usa = regional16h("usa", USA_16H_HOURS);
        PlayerMatchmakingData apac = regional16h("apac", APAC_16H_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(usa, apac)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(apac, usa)).isTrue();
    }

    @Test
    void usaAndSeaAwakeSixteenHoursAreNotMatched() {
        PlayerMatchmakingData usa = regional16h("usa", USA_16H_HOURS);
        PlayerMatchmakingData sea = regional16h("sea", SEA_16H_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(usa, sea)).isTrue();
        assertThat(MatchmakingCompatibilityService.areIncompatible(sea, usa)).isTrue();
    }

    @Test
    void apacAndSeaAwakeSixteenHoursAreMatched() {
        // APAC and SEA are only one timezone apart. With full 16h awake windows their overlap
        // grows from ~10h to ~15h, which clears even the strict 12-shared-hour bar. This is the
        // intended outcome for geographically close regions.
        PlayerMatchmakingData apac = regional16h("apac", APAC_16H_HOURS);
        PlayerMatchmakingData sea = regional16h("sea", SEA_16H_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(apac, sea)).isFalse();
        assertThat(MatchmakingCompatibilityService.areIncompatible(sea, apac)).isFalse();
    }

    @Test
    void twoEuAwakeSixteenHoursPlayersMatch() {
        PlayerMatchmakingData a = regional16h("eu1", EU_16H_HOURS);
        PlayerMatchmakingData b = regional16h("eu2", EU_16H_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isFalse();
    }

    @Test
    void twoUsaAwakeSixteenHoursPlayersMatch() {
        PlayerMatchmakingData a = regional16h("usa1", USA_16H_HOURS);
        PlayerMatchmakingData b = regional16h("usa2", USA_16H_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isFalse();
    }

    @Test
    void twoApacAwakeSixteenHoursPlayersMatch() {
        PlayerMatchmakingData a = regional16h("apac1", APAC_16H_HOURS);
        PlayerMatchmakingData b = regional16h("apac2", APAC_16H_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isFalse();
    }

    @Test
    void twoSeaAwakeSixteenHoursPlayersMatch() {
        PlayerMatchmakingData a = regional16h("sea1", SEA_16H_HOURS);
        PlayerMatchmakingData b = regional16h("sea2", SEA_16H_HOURS);

        assertThat(MatchmakingCompatibilityService.areIncompatible(a, b)).isFalse();
    }

    // The 12-hour profiles below only hold enough active hour data for the loose level (10 of 12),
    // while the 16-hour profiles hold enough for the strict level (12 of 14).
    private static PlayerMatchmakingData regional(String userId, Set<Integer> hours) {
        return regional(userId, hours, MatchmakingOptions.LOOSE_SIMILAR_ACTIVE_HOURS_OPTION);
    }

    private static PlayerMatchmakingData regional16h(String userId, Set<Integer> hours) {
        return regional(userId, hours, MatchmakingOptions.STRICT_SIMILAR_ACTIVE_HOURS_OPTION);
    }

    private static PlayerMatchmakingData regional(String userId, Set<Integer> hours, String level) {
        return player(userId)
                .restrictions(level)
                .activeHours(hours.toArray(new Integer[0]))
                .build();
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
        private Set<Integer> activeHours = ALL_HOURS;
        private int completedGames = 5;
        private Set<String> roleNames = Set.of();
        private Duration queueWait = Duration.ZERO;
        private boolean tigl;
        private List<String> tiglRanks = List.of(MatchmakingOptions.UNRANKED_OPTION);

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

        private Builder activeHours(Integer... values) {
            activeHours = Set.of(values);
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

        private Builder tigl(boolean value) {
            tigl = value;
            return this;
        }

        private Builder tiglRanks(String... values) {
            tiglRanks = List.of(values);
            return this;
        }

        private PlayerMatchmakingData build() {
            return new PlayerMatchmakingData(
                    userId,
                    restrictions,
                    avoidList,
                    rating,
                    activeHours,
                    completedGames,
                    roleNames,
                    queueWait,
                    tigl,
                    tiglRanks);
        }
    }
}
