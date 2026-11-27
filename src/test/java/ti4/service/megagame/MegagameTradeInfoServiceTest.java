package ti4.service.megagame;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class MegagameTradeInfoServiceTest extends BaseTi4Test {

    @Test
    void thirtyFactionsSplitIntoTwoMenusWithUniqueIds() {
        Game game = new Game();
        game.newGameSetup();
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Player player = game.addPlayer("user" + i, "User " + i);
            player.setFaction("faction" + i);
            player.setColor("color" + i);
            players.add(player);
        }

        List<StringSelectMenu> menus = MegagameTradeInfoService.factionMenus(players);

        assertThat(menus).extracting(menu -> menu.getOptions().size()).containsExactly(25, 5);
        assertThat(menus).extracting(StringSelectMenu::getCustomId).doesNotHaveDuplicates();
        assertThat(menus.getLast().getOptions())
                .extracting(SelectOption::getValue)
                .contains("faction29");
    }

    @Test
    void tradeButtonCarriesTheTargetFactionWithinDiscordLimits() {
        Game game = MegagameGroupServiceTest.gameWithCards();
        Player hacan = MegagameGroupServiceTest.player(game, "hacan");

        var button = MegagameTradeInfoService.tradeWithButton(hacan);

        assertThat(button.getCustomId()).isEqualTo(MegagameTradeInfoService.TRADE_WITH + "hacan");
        assertThat(button.getLabel()).isEqualTo("Trade with Hacan");
    }
}
