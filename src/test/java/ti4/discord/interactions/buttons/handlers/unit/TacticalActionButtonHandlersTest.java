package ti4.discord.interactions.buttons.handlers.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.tactical.TacticalActionService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.testUtils.BaseTi4Test;

class TacticalActionButtonHandlersTest extends BaseTi4Test {

    @Test
    void staleLandingButtonDoesNotRemoveUnitsAfterAvernusMovesToAnotherSystem() {
        Game game = new Game();
        Player player = game.addPlayer("player", "Player");
        player.setColor("chocolate");
        player.setFaction("muaat");
        Tile source = new Tile("fracture4", "frac4");
        Tile destination = new Tile("18", "000");
        Planet avernus = new Planet("avernus", null);
        destination.getUnitHolders().put("avernus", avernus);
        game.setTile(source);
        game.setTile(destination);
        var infantry = Units.getUnitKey(UnitType.Infantry, player.getColor());
        source.getSpaceUnitHolder().addUnit(infantry, 2);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        List<Button> refreshedButtons = List.of(Button.danger("deleteButtons", "Done"));

        try (MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class);
                MockedStatic<TacticalActionService> tactical = mockStatic(TacticalActionService.class);
                MockedStatic<RemoveUnitService> remove = mockStatic(RemoveUnitService.class);
                MockedStatic<AddUnitService> add = mockStatic(AddUnitService.class)) {
            tactical.when(() -> TacticalActionService.getLandingTroopsButtons(game, player, source))
                    .thenReturn(refreshedButtons);

            TacticalActionButtonHandlers.landingUnits("landUnits_frac4_2gf_avernus_chocolate", event, game, player);

            assertThat(source.getSpaceUnitHolder().getUnitCount(infantry)).isEqualTo(2);
            assertThat(avernus.getUnitCount(infantry)).isZero();
            remove.verifyNoInteractions();
            add.verifyNoInteractions();
            messages.verify(() -> MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That planet is no longer in this system. No units were moved. Refreshed landing buttons."));
            messages.verify(() -> MessageHelper.editMessageButtons(event, refreshedButtons));
        }
    }
}
