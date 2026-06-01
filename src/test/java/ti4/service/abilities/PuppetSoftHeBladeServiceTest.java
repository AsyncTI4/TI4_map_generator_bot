package ti4.service.abilities;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class PuppetSoftHeBladeServiceTest extends BaseTi4Test {

    @Test
    void frankenFlipOnlySwapsFirmamentComponents() throws Exception {
        Game game = new Game();
        game.newGameSetup();

        Player player = game.addPlayer("player1", "player1");
        player.setFaction("franken12");
        player.setColor("blu");

        Player otherPlayer = game.addPlayer("player2", "player2");
        otherPlayer.setFaction("franken13");
        otherPlayer.setColor("red");
        otherPlayer.addTech("parasite-firm_y");
        otherPlayer.addTech("planesplitter-firm");

        Tile home = new Tile("96a", "101");
        game.setTile(home);
        player.setHomeSystemPosition("101");

        player.addAbility("plotsplots");
        player.addAbility("puppetsoftheblade");
        player.addAbility("scheming");
        player.addLeader("firmamentagent");
        player.addLeader("firmamentcommander");
        player.addLeader("yinhero");
        player.addFactionTech("planesplitter-firm");
        player.addFactionTech("parasite-firm_y");
        player.addTech("planesplitter-firm");
        player.addTech("parasite-firm_y");
        player.addOwnedUnitByID("firmament_mech");
        player.addOwnedUnitByID("firmament_flagship");
        player.addBreakthrough("firmamentbt");
        player.addOwnedPromissoryNoteByID("blackops");
        player.setPromissoryNote("blackops");
        player.setPlotCard("seethe", 11);
        player.setPlotCard("mutated_seethe", 12);
        player.setPlotCardFaction("seethe", "other");
        player.setPlotCardFaction("mutated_seethe", "other");

        player.addToStoredList(
                "appliedFrankenItems",
                "HOMESYSTEM:firmament",
                "ABILITY:plotsplots",
                "ABILITY:puppetsoftheblade",
                "TECH:planesplitter-firm",
                "TECH:parasite-firm_y",
                "AGENT:firmamentagent",
                "COMMANDER:firmamentcommander",
                "MECH:firmament_mech",
                "FLAGSHIP:firmament_flagship",
                "PN:blackops",
                "BREAKTHROUGH:firmamentbt",
                "PLOT:seethe",
                "PLOT:mutated_seethe");

        invokeFlip(game, player);

        assertThat(player.hasAbility("plotsplots")).isFalse();
        assertThat(player.hasAbility("puppetsoftheblade")).isFalse();
        assertThat(player.hasAbility("marionettes")).isTrue();
        assertThat(player.hasAbility("bladesorchestra")).isTrue();
        assertThat(player.hasAbility("scheming")).isTrue();

        assertThat(player.hasLeader("firmamentagent")).isFalse();
        assertThat(player.hasLeader("firmamentcommander")).isFalse();
        assertThat(player.hasLeader("obsidianagent")).isTrue();
        assertThat(player.hasLeader("obsidiancommander")).isTrue();
        assertThat(player.hasLeader("obsidianhero")).isFalse();
        assertThat(player.hasLeader("yinhero")).isTrue();

        assertThat(player.getFactionTechs()).contains("planesplitter-obs").doesNotContain("planesplitter-firm");
        assertThat(player.getFactionTechs()).contains("parasite-obs_y").doesNotContain("parasite-firm_y");
        assertThat(otherPlayer.hasTech("parasite-firm_y")).isTrue();
        assertThat(otherPlayer.hasTech("planesplitter-firm")).isTrue();
        assertThat(otherPlayer.hasTech("parasite-obs_y")).isFalse();
        assertThat(otherPlayer.hasTech("planesplitter-obs")).isFalse();
        assertThat(player.hasUnit("obsidian_mech")).isTrue();
        assertThat(player.hasUnit("obsidian_flagship")).isTrue();
        assertThat(player.hasBreakthrough("obsidianbt")).isTrue();
        assertThat(player.getPromissoryNotesOwned()).contains("malevolency").doesNotContain("blackops");
        assertThat(player.getPromissoryNotes().keySet()).contains("malevolency").doesNotContain("blackops");

        assertThat(player.getHomeSystemTile().getTileID()).isEqualTo("96b");
        assertThat(player.getPlotCards()).containsKeys("seethe", "mutated_seethe");
        assertThat(player.getPuppetedFactionsForPlot("seethe")).containsExactly("other");
        assertThat(player.getPuppetedFactionsForPlot("mutated_seethe")).containsExactly("other");

        assertThat(player.getStoredList("appliedFrankenItems"))
                .contains("HOMESYSTEM:obsidian")
                .contains("ABILITY:marionettes")
                .contains("ABILITY:bladesorchestra")
                .contains("TECH:planesplitter-obs")
                .contains("TECH:parasite-obs_y")
                .contains("AGENT:obsidianagent")
                .contains("COMMANDER:obsidiancommander")
                .contains("MECH:obsidian_mech")
                .contains("FLAGSHIP:obsidian_flagship")
                .contains("PN:malevolency")
                .contains("BREAKTHROUGH:obsidianbt")
                .contains("PLOT:seethe")
                .contains("PLOT:mutated_seethe")
                .doesNotContain(
                        "HOMESYSTEM:firmament",
                        "ABILITY:plotsplots",
                        "ABILITY:puppetsoftheblade",
                        "TECH:planesplitter-firm",
                        "TECH:parasite-firm_y",
                        "AGENT:firmamentagent",
                        "COMMANDER:firmamentcommander",
                        "MECH:firmament_mech",
                        "FLAGSHIP:firmament_flagship",
                        "PN:blackops",
                        "BREAKTHROUGH:firmamentbt");
    }

    @Test
    void frankenFlipWithoutFirmamentHomeSkipsHomeSwap() throws Exception {
        Game game = new Game();
        game.newGameSetup();

        Player player = game.addPlayer("player1", "player1");
        player.setFaction("franken12");
        player.setColor("blu");

        player.addAbility("plotsplots");
        player.addAbility("puppetsoftheblade");
        player.addFactionTech("parasite-firm_y");
        player.addLeader("firmamentagent");
        player.addOwnedUnitByID("firmament_mech");
        player.setPlotCard("seethe", 11);

        player.addToStoredList(
                "appliedFrankenItems",
                "ABILITY:plotsplots",
                "ABILITY:puppetsoftheblade",
                "TECH:parasite-firm_y",
                "AGENT:firmamentagent",
                "MECH:firmament_mech",
                "PLOT:seethe");

        invokeFlip(game, player);

        assertThat(player.hasAbility("plotsplots")).isFalse();
        assertThat(player.hasAbility("marionettes")).isTrue();
        assertThat(player.getFactionTechs()).contains("parasite-obs_y").doesNotContain("parasite-firm_y");
        assertThat(player.hasLeader("obsidianagent")).isTrue();
        assertThat(player.hasUnit("obsidian_mech")).isTrue();
        assertThat(player.getPlotCards()).containsKeys("seethe");
        assertThat(player.getHomeSystemTile()).isNull();
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeFlip(Game game, Player player) throws Exception {
        Method method = PuppetSoftHeBladeService.class.getDeclaredMethod(
                "flipFirmamentComponentsToObsidianComponents", Game.class, Player.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(null, game, player);
    }
}
