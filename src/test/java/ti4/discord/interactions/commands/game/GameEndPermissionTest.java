package ti4.discord.interactions.commands.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

/**
 * /game end only checked that it ran in a channel named after the game, so anyone who could post there (spectators,
 * observers, other server members) could end and archive someone else's game.
 */
class GameEndPermissionTest extends BaseTi4Test {

    private final Role bothelperRole = mock(Role.class);
    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("pbd123");
        game.addPlayer("playerUser", "Player");
        JdaService.bothelperRoles.add(bothelperRole);
    }

    @AfterEach
    void tearDown() {
        JdaService.bothelperRoles.remove(bothelperRole);
    }

    @Test
    void aPlayerInTheGameMayEndIt() {
        assertThat(GameEnd.mayEndGame(game, eventFrom("playerUser", List.of()))).isTrue();
    }

    @Test
    void aBothelperOutsideTheGameMayEndIt() {
        assertThat(GameEnd.mayEndGame(game, eventFrom("helperUser", List.of(bothelperRole))))
                .isTrue();
    }

    @Test
    void someoneOutsideTheGameMayNotEndIt() {
        assertThat(GameEnd.mayEndGame(game, eventFrom("outsiderUser", List.of())))
                .isFalse();
    }

    private static SlashCommandInteractionEvent eventFrom(String userId, List<Role> roles) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        Member member = mock(Member.class);
        when(member.getRoles()).thenReturn(roles);
        when(member.getUser()).thenReturn(user);
        when(event.getUser()).thenReturn(user);
        when(event.getMember()).thenReturn(member);
        return event;
    }
}
