package ti4.service.tournament;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.service.tournament.TournamentMemberResolver.Candidate;
import ti4.service.tournament.TournamentMemberResolver.Resolution;

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
