package ti4.spring.service;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ti4.map.Game;
import ti4.map.Player;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ActionCardDeckService.class)
class ActionCardDeckServiceTest {

    @Autowired
    private ActionCardDeckService service;

    @Test
    void shuffleCallsGameShuffle() {
        Game game = Mockito.mock(Game.class);
        Player player = Mockito.mock(Player.class);
        MessageChannel channel = Mockito.mock(MessageChannel.class);
        //when(game.getActionsChannel()).thenReturn(channel);
        when(player.getRepresentationNoPing()).thenReturn("p");

        service.shuffle(game, player);

        verify(game).shuffleActionCards();
    }
}
