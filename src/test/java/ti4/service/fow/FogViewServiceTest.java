package ti4.service.fow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.componentsV2.ComponentPanel;
import ti4.testUtils.BaseTi4Test;

class FogViewServiceTest extends BaseTi4Test {

    @Test
    void thePanelFitsDiscordForEitherLayout() {
        Game game = new Game();
        game.newGameSetup();
        game.setFowMode(true);
        Player player = game.addPlayer("player-id", "sol");
        player.setFaction("sol");

        assertThat(ComponentPanel.fits(FogViewService.components(player, "Changed: ghost hexes.")))
                .isTrue();
        player.setFogOverviewBoxes(true);
        assertThat(ComponentPanel.fits(FogViewService.components(player, null))).isTrue();
    }

    @Test
    void everyColourMapsBothWays() {
        for (String label : FogViewService.filterLabels()) {
            String suffix = FogViewService.filterSuffixFor(label);
            assertThat(suffix).isNotNull();
            assertThat(FogViewService.filterLabelFor(suffix)).isEqualTo(label);
        }
        // Old spellings people typed into /user set_fog_filter still work.
        assertThat(FogViewService.filterSuffixFor("grey")).isEqualTo("default");
        assertThat(FogViewService.filterLabelFor("unknown")).isEqualTo(Constants.FOW_FILTER_DARK_GREY);
    }
}
