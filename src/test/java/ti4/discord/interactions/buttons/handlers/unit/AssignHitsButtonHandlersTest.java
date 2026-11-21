package ti4.discord.interactions.buttons.handlers.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

class AssignHitsButtonHandlersTest extends BaseTi4Test {

    // The Mobile Mountain planet is exhausted while the Stoneborn flagship is damaged. Auto-assign kept the two in
    // sync, but a manual Sustain button press did not.
    @Test
    void manualSustainOnMobileMountainExhaustsThePlanet() {
        Game game = new Game();
        Tile tile = new Tile("19", "101");
        game.setTile(tile);
        Player player = game.addPlayer("redId", "redUser");
        player.setFaction("stoneborn");
        player.setColor("red");
        player.setFactionEmoji("a");
        player.addOwnedUnitByID("stoneborn_flagship");
        player.addPlanet("mobilemountain");
        player.refreshPlanet("mobilemountain");
        UnitKey flagship = Units.getUnitKey(UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagship, 1);

        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            AssignHitsButtonHandlers.assignDamage("assignDamage_101_1_fs_red", event, game, player);
        }

        assertEquals(1, tile.getSpaceUnitHolder().getDamagedUnitCount(flagship));
        assertFalse(player.hasPlanetReady("mobilemountain"));
    }
}
