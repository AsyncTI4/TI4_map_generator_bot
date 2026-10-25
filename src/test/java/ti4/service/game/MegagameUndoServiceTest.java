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
