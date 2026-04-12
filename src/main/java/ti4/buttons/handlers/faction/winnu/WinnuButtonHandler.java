package ti4.buttons.handlers.faction.winnu;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.unit.AddUnitService;

@UtilityClass
class WinnuButtonHandler {

    @ButtonHandler("winnuStructure_")
    public static void winnuStructure(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String replaced = buttonID.replace("winnuStructure_", "");
        String[] split = replaced.split("_");
        String unit = split[0];
        String planet = split[1];
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));
        AddUnitService.addUnits(event, tile, game, player.getColor(), unit + " " + planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " placed a " + unit + " on " + Helper.getPlanetRepresentation(planet, game)
                        + ".");
        CommanderUnlockCheckService.checkPlayer(player, "titans", "saar", "rohdhna", "cheiran", "celdauri");
    }
}
