package ti4.service.tournament;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.service.tournament.TournamentMemberResolver.Candidate;
import ti4.service.tournament.TournamentMemberResolver.Resolution;
import ti4.settings.users.UserSettings;
import ti4.spring.service.tournament.Tournament;

class TournamentHelpersTest {

    private static final String ALICE = "111111111111111111";
    private static final String BOB = "222222222222222222";
    private static final String BOBBY = "333333333333333333";

    @Test
    void resolverHandlesMentionsIdsNamesAndProblems() {
        List<Candidate> candidates = List.of(
                new Candidate(ALICE, "alice", List.of("Alice A")),
                new Candidate(BOB, "bob", List.of("Captain")),
                new Candidate(BOBBY, "bobby", List.of("Captain", "Bob")));

        Resolution resolution = TournamentMemberResolver.resolveAmong(
                "<@" + ALICE + ">\n" + BOBBY + ", @bob\nCaptain\nnobody\n<@!" + ALICE + ">", candidates);

        // "@bob" is BOB's unique username even though BOBBY's display name is also "Bob".
        assertThat(resolution.userIds()).containsExactly(ALICE, BOBBY, BOB);
        assertThat(resolution.ambiguous()).containsExactly("Captain");
        assertThat(resolution.notFound()).containsExactly("nobody");
    }

    @Test
    void hourProfilesFallBackFromActivityToAfkToTimeZone() {
        UserSettings settings = new UserSettings();
        settings.setUtcOffsetMinutes(60);
        // Central Europe 15:00-23:00 local is 14:00-22:00 UTC.
        assertThat(TournamentHours.profile(settings).source()).isEqualTo(TournamentHours.Source.TIME_ZONE);
        assertThat(TournamentHours.profile(settings).hours()).containsExactly(14, 15, 16, 17, 18, 19, 20, 21);

        settings.setAfkHours("0;1;2;3;4;5;6;7");
        assertThat(TournamentHours.profile(settings).source()).isEqualTo(TournamentHours.Source.AFK_SETTINGS);
        assertThat(TournamentHours.profile(settings).hours())
                .hasSize(16)
                .doesNotContain(0, 7)
                .contains(8, 23);

        // 300 check-ins, all at 19:00 UTC: enough activity data to win over the fallbacks.
        settings.setActiveHours("0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;300;0;0;0;0");
        assertThat(TournamentHours.profile(settings).source()).isEqualTo(TournamentHours.Source.ACTIVITY);
        assertThat(TournamentHours.profile(settings).hours()).containsExactly(19);

        assertThat(TournamentHours.profile(new UserSettings()).source()).isEqualTo(TournamentHours.Source.NONE);
    }

    @Test
    void timeZoneHoursWrapAroundMidnightAndHandleHalfHours() {
        // India 15:00-23:00 local starts at 09:30 UTC, counted from hour 9.
        assertThat(TournamentHours.likelyActiveUtcHours(330)).containsExactly(9, 10, 11, 12, 13, 14, 15, 16);
        // US Pacific 15:00-23:00 local is 23:00-07:00 UTC.
        assertThat(TournamentHours.likelyActiveUtcHours(-480)).containsExactly(23, 0, 1, 2, 3, 4, 5, 6);
        assertThat(TournamentPlayTimes.offsets()).hasSize(25);
        assertThat(TournamentPlayTimes.label(330)).isEqualTo("UTC+5:30");
        assertThat(TournamentPlayTimes.label(-300)).isEqualTo("UTC-5");
    }

    @Test
    void playTimesRankBelowAfkAndAboveTheTimeZoneGuess() {
        UserSettings settings = new UserSettings();
        settings.setUtcOffsetMinutes(60);
        assertThat(TournamentHours.needsPlayTimes(settings)).isTrue();

        // Central Europe: Morning 08-11 and Evening 17-20 local, with a gap in between.
        settings.setPlayTimeParts(List.of("MORNING", "EVENING", "unknown"));
        assertThat(TournamentHours.profile(settings).source()).isEqualTo(TournamentHours.Source.PLAY_TIMES);
        assertThat(TournamentHours.profile(settings).hours()).containsExactly(7, 8, 9, 16, 17, 18);
        assertThat(TournamentHours.needsPlayTimes(settings)).isFalse();

        // Late night 02-05 in US Pacific wraps to 10-13 UTC.
        settings.setUtcOffsetMinutes(-480);
        settings.setPlayTimeParts(List.of("LATE_NIGHT"));
        assertThat(TournamentHours.profile(settings).hours()).containsExactly(10, 11, 12);

        settings.setAfkHours("0;1;2;3;4;5;6;7");
        assertThat(TournamentHours.profile(settings).source()).isEqualTo(TournamentHours.Source.AFK_SETTINGS);
    }

    @Test
    void pacingSlowsDownAfterSlowStepsAndWhenDiscordIsLaggy() {
        assertThat(TournamentPacing.pauseAfter(3000, 80)).isEqualTo(TournamentPacing.BASE_PAUSE_MILLIS);
        // A 25 s game creation earns a 25 s breather before the next one.
        assertThat(TournamentPacing.pauseAfter(25_000, 80)).isEqualTo(25_000);
        assertThat(TournamentPacing.pauseAfter(3000, 900)).isEqualTo(4 * TournamentPacing.BASE_PAUSE_MILLIS);
        assertThat(TournamentPacing.pauseAfter(40_000, 900)).isEqualTo(TournamentPacing.MAX_PAUSE_MILLIS);
    }

    @Test
    void customGameNamesFollowTheChannelNameRules() {
        assertThat(TournamentGameNames.normalize("  T8P ")).isEqualTo("t8p");
        assertThat(TournamentGameNames.problem("t8p")).isEmpty();
        assertThat(TournamentGameNames.problem("tourney7semi")).isEmpty();
        // A dash would break finding the game from its channel names (name-before-first-dash).
        assertThat(TournamentGameNames.problem("t8-p")).isPresent();
        assertThat(TournamentGameNames.problem("8tp")).isPresent();
        assertThat(TournamentGameNames.problem("t")).isPresent();
        assertThat(TournamentGameNames.problem("pbdtourney")).isPresent();
        assertThat(TournamentGameNames.problem("fowcup")).isPresent();

        var tournament = new Tournament();
        tournament.setGameNamePrefix("t8p");
        assertThat(TournamentGameNames.customName(tournament, 4)).isEqualTo("t8p04");
        assertThat(TournamentGameNames.customName(tournament, 12)).isEqualTo("t8p12");
        tournament.setFogMode(true);
        assertThat(TournamentGameNames.usesCustomNames(tournament)).isFalse();
    }

    @Test
    void inviteChannelAcceptsLinksMentionsAndIds() {
        String id = "444444444444444444";
        assertThat(TournamentInvites.channelId("https://discord.com/channels/1/" + id))
                .isEqualTo(id);
        assertThat(TournamentInvites.channelId("https://discord.com/channels/1/" + id + "/555"))
                .isEqualTo(id);
        assertThat(TournamentInvites.channelId("<#" + id + ">")).isEqualTo(id);
        assertThat(TournamentInvites.channelId(" " + id + " ")).isEqualTo(id);
        assertThat(TournamentInvites.channelId("#general")).isNull();
    }

    @Test
    void gameMastersRotateAndNeverRunTheirOwnGroup() {
        Map<Integer, List<String>> groups = Map.of(
                1, List.of("gm1", "p1"),
                2, List.of("p2", "p3"),
                3, List.of("p4", "gm2"),
                4, List.of("gm1", "gm2"));

        Map<Integer, String> assigned = TournamentGameMasterAssigner.assign(List.of("gm1", "gm2"), groups);

        assertThat(assigned).containsEntry(1, "gm2").containsEntry(2, "gm1").containsEntry(3, "gm1");
        // Both GMs play in group 4, so it stays without a GM.
        assertThat(assigned).doesNotContainKey(4);
    }

    @Test
    void setupOptionsApplyStoredChoiceOrDefaultAndCycle() {
        List<String> appliedTo = new ArrayList<>();
        TournamentSetupOption decks = option("decks", List.of("pok", "te"), appliedTo);
        TournamentSetupOption milty = option("milty", List.of("standard", "nucleus"), appliedTo);

        List<String> summary =
                TournamentSetupOptions.apply(mock(Game.class), Map.of("milty", "nucleus"), List.of(decks, milty));

        assertThat(appliedTo).containsExactly("decks=pok", "milty=nucleus");
        assertThat(summary).containsExactly("decks: pok", "milty: nucleus");
        assertThat(TournamentSetupOptions.nextChoice(milty, "nucleus")).isEqualTo("standard");
    }

    @Test
    void tourneyModRoleOrAdminRoleGrantsAccess() {
        Role adminRole = role("Admin");
        Role tourneyMod = role("tourney mod");
        Role bothelper = role("Bothelper");

        assertThat(TournamentAccess.hasTournamentAdminRole(List.of(tourneyMod), List.of(adminRole)))
                .isTrue();
        assertThat(TournamentAccess.hasTournamentAdminRole(List.of(adminRole), List.of(adminRole)))
                .isTrue();
        assertThat(TournamentAccess.hasTournamentAdminRole(List.of(bothelper), List.of(adminRole)))
                .isFalse();
    }

    @Test
    void jobGuardAllowsOneRunAndDropsTasksTheExecutorNeverStarted() {
        AtomicLong now = new AtomicLong(0);
        TournamentJobGuard<Long> guard = new TournamentJobGuard<>(now::get);
        List<Runnable> queued = new ArrayList<>();
        List<String> ran = new ArrayList<>();

        assertThat(guard.submit(1L, () -> ran.add("first"), queued::add)).isTrue();
        // Claimed at submit time, so a second press before the task starts is refused.
        assertThat(guard.isRunning(1L)).isTrue();
        assertThat(guard.submit(1L, () -> ran.add("second"), queued::add)).isFalse();

        // The executor dropped the first task (e.g. circuit breaker); the claim expires.
        now.set(TournamentJobGuard.UNSTARTED_TIMEOUT_MILLIS);
        assertThat(guard.isRunning(1L)).isFalse();
        assertThat(guard.submit(1L, () -> ran.add("third"), queued::add)).isTrue();

        // If the dropped task starts late after all, it must not run next to the new one.
        queued.getFirst().run();
        queued.getLast().run();
        assertThat(ran).containsExactly("third");
        assertThat(guard.isRunning(1L)).isFalse();
    }

    @Test
    void funNameKeepsTheGroupNumberAndFitsTheGivenLength() {
        Tournament tournament = new Tournament();
        tournament.setSeriesName("A".repeat(90));
        tournament.setRoundLabel("Quarterfinals");

        String name = TournamentGameCreator.funName(tournament, 7, 40);

        assertThat(name).hasSize(40).endsWith(" 07");
    }

    private static Role role(String name) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(name);
        return role;
    }

    private static TournamentSetupOption option(String id, List<String> choices, List<String> appliedTo) {
        return new TournamentSetupOption() {
            public String id() {
                return id;
            }

            public String label() {
                return id;
            }

            public String description() {
                return "";
            }

            public List<String> choices() {
                return choices;
            }

            public void apply(Game game, String choice) {
                appliedTo.add(id + "=" + choice);
            }
        };
    }
}
