package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.AliasHandler;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class RearmamentAgendaResolver implements ForAgainstAgendaResolver {
    @Override
    public String getAgendaId() {
        return "rearmament";
    }

    @Override
    public void handleFor(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        for (Player player : game.getRealPlayers()) {
            String message = player.getRepresentation() + ", please choose a home system planet to place a mech on.";
            MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                message,
                Helper.getHSPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
        }
    }

    @Override
    public void handleAgainst(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        for (Player player : game.getRealPlayers()) {
            for (Tile tile : game.getTileMap().values()) {
                for (UnitHolder capChecker : tile.getUnitHolders().values()) {
                    int count = capChecker.getUnitCount(UnitType.Mech, player.getColor());
                    if (count > 0) {
                        String colorID = Mapper.getColorID(player.getColor());
                        var mechKey = Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), colorID);
                        var infKey = Mapper.getUnitKey(AliasHandler.resolveUnit("inf"), colorID);
                        capChecker.removeUnit(mechKey, count);
                        capChecker.addUnit(infKey, count);
                    }
                }
            }
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Removed all mechs.");
    }
}
