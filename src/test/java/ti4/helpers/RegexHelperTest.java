package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class RegexHelperTest extends BaseTi4Test {

    @Test
    void acRegexForPlayerShouldMatchCardsOnlyInHand() {
        var game = new Game();
        game.setActionCards(new ArrayList<>());
        var player = new Player("player1", "player1", game);
        player.getActionCards().put("mobilization3", 7);

        var pattern = Pattern.compile("handleRalNelAgent_" + RegexHelper.acRegex(game, player));
        var matcher = pattern.matcher("handleRalNelAgent_mobilization3");

        assertThat(matcher.matches()).isTrue();
        assertThat(matcher.group("ac")).isEqualTo("mobilization3");
    }
}
