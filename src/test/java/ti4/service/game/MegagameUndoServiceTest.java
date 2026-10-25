package ti4.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.testbed.TestBedService;
import ti4.settings.GlobalSettings;
import ti4.settings.GlobalSettings.ImplementedSettings;
import ti4.testUtils.BaseTi4Test;

class MegagameUndoServiceTest extends BaseTi4Test {

    @Test
    void untaggedGameLetsAnyoneUndo() {
        assertThat(MegagameUndoService.mayUndo(gameWithTags(), memberWithRoles()))
                .isTrue();
    }

    @Test
    void megagameRefusesPlayersWithoutUndoRole() {
        Game game = gameWithTags("Megagame");
        assertThat(MegagameUndoService.mayUndo(game, memberWithRoles("pbd100three")))
                .isFalse();
        assertThat(MegagameUndoService.mayUndo(game, null)).isFalse();
    }

    @Test
    void megagameAllowsGmAndBothelperRoles() {
        Game game = gameWithTags("megagame");
        assertThat(MegagameUndoService.mayUndo(game, memberWithRoles("pbd100three GM")))
                .isTrue();
        assertThat(MegagameUndoService.mayUndo(game, memberWithRoles("Bothelper")))
                .isTrue();
    }

    @Test
    void undoRequestPingsGmRoleAndFallsBackToPlainNameWithoutGuild() {
        Role gmRole = mock(Role.class);
        when(gmRole.getAsMention()).thenReturn("<@&42>");
        Guild guild = mock(Guild.class);
        when(guild.getRolesByName("pbd100three GM", true)).thenReturn(List.of(gmRole));

        assertThat(MegagameUndoService.undoRequestMessage(guild, "Alice", "/tech add"))
                .startsWith("<@&42> Undo has been disabled please wait for a GM to handle your request.")
                .contains("Alice", "/tech add");
        assertThat(MegagameUndoService.undoRequestMessage(null, "Alice", "/tech add"))
                .startsWith("@pbd100three GM ");
    }

    @Test
    void turnEndsFollowsSecondariesAndAgendaPhaseAreProtected() {
        Game game = megagameWithPlayer();
        assertThat(protectedAfter(game, "alice pressed button: End Turn (FFCC_sol_turnEnd)"))
                .isTrue();
        assertThat(protectedAfter(game, "alice pressed button: Pass (passForRound)"))
                .isTrue();
        assertThat(protectedAfter(game, "alice used: /player pass")).isTrue();
        assertThat(protectedAfter(game, "alice pressed button: Spend A Strategy Token (sc_follow_2)"))
                .isTrue();
        assertThat(protectedAfter(game, "alice pressed button: Ready 2 Planets (diploRefresh2)"))
                .isTrue();
        assertThat(protectedAfter(game, "alice pressed button: Draw Secret Objective (sc_draw_so)"))
                .isTrue();

        game.setLatestCommand("alice pressed button: Move (tacticalMoveFrom_101)");
        game.setPhaseOfGame("agendaVoting");
        assertThat(MegagameUndoService.isProtectedStep(game)).isTrue();
    }

    @Test
    void ordinaryStepsAndPrimariesStayUndoable() {
        Game game = megagameWithPlayer();
        assertThat(protectedAfter(game, "alice pressed button: Move (tacticalMoveFrom_101)"))
                .isFalse();
        assertThat(protectedAfter(game, "alice pressed button: Warfare Primary (primaryOfWarfare)"))
                .isFalse();
        assertThat(protectedAfter(game, "alice used: /player passive_ability")).isFalse();
        assertThat(protectedAfter(game, "someone pressed button: [CLASSIFIED]")).isFalse();
    }

    @Test
    void protectedStepsStayUndoableForGmsAndWithoutStrictUndoTag() {
        Game game = megagameWithPlayer();
        game.setLatestCommand("alice pressed button: End Turn (turnEnd)");
        assertThat(MegagameUndoService.mayUndoLatestStep(game, game.getPlayer("aliceId"), memberWithRoles()))
                .isFalse();
        assertThat(MegagameUndoService.mayUndoLatestStep(
                        game, game.getPlayer("aliceId"), memberWithRoles("pbd100three GM")))
                .isTrue();

        // The megagame tag alone only guards /game undo; the button rules need the strict tag too
        game.setTags(new ArrayList<>(List.of("megagame")));
        assertThat(MegagameUndoService.mayUndoLatestStep(game, game.getPlayer("aliceId"), memberWithRoles()))
                .isTrue();
    }

    // Testbed presses carry the developer's Member; a virtual seat must not inherit their bothelper role.
    @Test
    void testBedVirtualSeatGetsNoRoleBypass() {
        Game game = megagameWithPlayer();
        game.setLatestCommand("alice pressed button: End Turn (turnEnd)");
        Player seat = game.addPlayer(TestBedService.VIRTUAL_SEAT_ID_PREFIX + "1", "TestSeat1");
        TestBedService.markAsTestBed(game, true);
        String key = ImplementedSettings.TESTBED_ENABLED.toString();
        try {
            GlobalSettings.setSetting(key, "true");
            assertThat(MegagameUndoService.mayUndoLatestStep(game, seat, memberWithRoles("bothelper")))
                    .isFalse();
            assertThat(MegagameUndoService.mayUndoLatestStep(
                            game, game.getPlayer("aliceId"), memberWithRoles("bothelper")))
                    .isTrue();
        } finally {
            GlobalSettings.setSetting(key, false);
        }
    }

    private static boolean protectedAfter(Game game, String latestCommand) {
        game.setLatestCommand(latestCommand);
        return MegagameUndoService.isProtectedStep(game);
    }

    private static Game megagameWithPlayer() {
        Game game = gameWithTags("megagame", "megagame-strict-undo");
        game.newGameSetup();
        game.setPhaseOfGame("action");
        Player player = game.addPlayer("aliceId", "alice");
        player.setFaction("sol");
        player.setColor("blue");
        return game;
    }

    private static Game gameWithTags(String... tags) {
        Game game = new Game();
        game.setTags(new ArrayList<>(List.of(tags)));
        return game;
    }

    private static Member memberWithRoles(String... roleNames) {
        List<Role> roles = new ArrayList<>();
        for (String name : roleNames) {
            Role role = mock(Role.class);
            when(role.getName()).thenReturn(name);
            roles.add(role);
        }
        Member member = mock(Member.class);
        when(member.getRoles()).thenReturn(roles);
        return member;
    }
}
