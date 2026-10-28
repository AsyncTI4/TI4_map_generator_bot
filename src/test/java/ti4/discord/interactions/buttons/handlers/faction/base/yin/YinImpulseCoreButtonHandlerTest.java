package ti4.discord.interactions.buttons.handlers.faction.base.yin;

import static org.assertj.core.api.Assertions.assertThat;

import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class YinImpulseCoreButtonHandlerTest extends BaseTi4Test {

    private Game game;
    private Player yin;
    private Tile tile;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("impulse-core-test");
        yin = game.addPlayer("yinUser", "Yin Player");
        yin.setFaction(game, "yin");
        yin.setColor("red");
        tile = new Tile("19", "101");
        game.setTile(tile);
    }

    private void addSpaceUnit(UnitType unitType) {
        tile.addUnit("space", Units.getUnitKey(unitType, yin.getColorID()), 1);
    }

    @Test
    void offeredWhenYinHasImpulseCoreAndACruiserInTheSystem() {
        yin.addTech("ic");
        addSpaceUnit(UnitType.Cruiser);

        Button button = YinImpulseCoreButtonHandler.getImpulseCoreButton(yin, tile);

        assertThat(button).isNotNull();
        assertThat(button.getCustomId()).endsWith("startImpulseCore_101");
        // The button must be restricted to the Yin player so the opponent cannot sacrifice Yin ships.
        assertThat(button.getCustomId()).startsWith(yin.factionButtonChecker());
    }

    @Test
    void offeredForADestroyerToo() {
        yin.addTech("ic");
        addSpaceUnit(UnitType.Destroyer);

        assertThat(YinImpulseCoreButtonHandler.getImpulseCoreButton(yin, tile)).isNotNull();
    }

    @Test
    void notOfferedWithoutTheTech() {
        addSpaceUnit(UnitType.Cruiser);

        assertThat(YinImpulseCoreButtonHandler.getImpulseCoreButton(yin, tile)).isNull();
    }

    @Test
    void notOfferedWithoutACruiserOrDestroyerToDestroy() {
        yin.addTech("ic");
        addSpaceUnit(UnitType.Dreadnought);
        addSpaceUnit(UnitType.Carrier);

        assertThat(YinImpulseCoreButtonHandler.getImpulseCoreButton(yin, tile)).isNull();
    }

    @Test
    void absolImpulseCoreIsAnActionAndDoesNotGetTheStartOfCombatButton() {
        yin.addTech("absol_ic");
        addSpaceUnit(UnitType.Cruiser);

        assertThat(YinImpulseCoreButtonHandler.getImpulseCoreButton(yin, tile)).isNull();
    }
}
