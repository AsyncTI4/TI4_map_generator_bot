package ti4.service.strategycard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class TradeStrategyCardServiceTest extends BaseTi4Test {

    // Playing Trade through a slash command passes a non-button event; the primary must still pay out.
    @Test
    void primaryGainsTradeGoodsWhenNotTriggeredByAButton() {
        Game game = new Game();
        Player player = game.addPlayer("user", "User");
        player.setFaction("hacan");
        player.setColor("yellow");
        player.setCommoditiesBase(6);
        player.setTg(1);

        TradeStrategyCardService.doPrimary(game, null, player);

        assertThat(player.getTg()).isEqualTo(4);
        assertThat(player.getCommodities()).isEqualTo(player.getCommoditiesTotal());
    }
}
