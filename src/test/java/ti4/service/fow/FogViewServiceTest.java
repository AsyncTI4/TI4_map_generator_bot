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
        Player player = game.addPlayer("fog-view-" + java.util.UUID.randomUUID(), "sol");
        player.setFaction("sol");

        assertThat(ComponentPanel.fits(FogViewService.components(player, "Changed: ghost hexes.")))
                .isTrue();
        player.setFogOverviewBoxes(true);
        player.setFogOverviewCodes(true);
        player.setFogOverviewReach(0);
        assertThat(ComponentPanel.fits(FogViewService.components(player, null))).isTrue();
    }

    @Test
    void choiceButtonsSetTheirSetting() {
        Player player = new Player("fog-view-" + java.util.UUID.randomUUID(), "player", new Game());

        FogViewService.apply(player, "layout", "hexes");
        FogViewService.apply(player, "labels", "names");
        FogViewService.apply(player, "islands", "0");
        FogViewService.apply(player, "ghosts", "off");
        FogViewService.apply(player, "islands", "x");

        assertThat(player.isFogOverviewBoxes()).isFalse();
        assertThat(player.isFogOverviewCodes()).isFalse();
        assertThat(player.getFogOverviewReach()).isZero();
        assertThat(player.isFogGhostHexes()).isFalse();
        // The longest faction prefix still keeps the id inside Discord's 100 characters.
        assertThat(FogViewService.choiceId("FFCC_" + "x".repeat(40) + "_", "islands", "4"))
                .hasSizeLessThan(100);
    }

    @Test
    void everyIslandChoiceHasALabel() {
        assertThat(FogViewService.reachChoices()).containsKeys(0, 1, 2, 3, 4);
        assertThat(FogViewService.reachChoices().get(0)).isEqualTo("Whole segments");
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
