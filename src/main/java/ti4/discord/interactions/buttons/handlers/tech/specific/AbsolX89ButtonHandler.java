package ti4.discord.interactions.buttons.handlers.tech.specific;

import java.util.HashSet;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
class AbsolX89ButtonHandler {

    @ButtonHandler("absolX89Nuke_")
    public static void absolX89Nuke(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        String planet = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFaction() + " used _X-89 Bacterial Weapon_ to remove all ground forces on " + planet + ".");

        Tile tile = game.getTileContainingPlanet(planet);
        UnitHolder uH = game.getPlanet(planet);
        Set<UnitKey> units = new HashSet<>(uH.getUnitsByState().keySet());
        for (UnitKey unit : units) {
            if (game.getPlayerByUnitKey(unit)
                    .map(p -> p.getUnitFromUnitKey(unit))
                    .map(UnitModel::getIsGroundForce)
                    .orElse(false)) {
                int amt = uH.getUnitCount(unit);
                DestroyUnitService.destroyUnit(event, tile, game, unit, amt, uH, true);
            }
        }
    }
}
