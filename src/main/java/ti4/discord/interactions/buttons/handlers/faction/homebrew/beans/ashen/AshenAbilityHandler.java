package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class AshenAbilityHandler {
    private static final String USE_CINDERBORN = "useCinderbornDeploy_";

    public static Button getCinderbornButton(Player player, String nameOfHolder) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_CINDERBORN + nameOfHolder,
                "Use Cinderborn II Deploy",
                FactionEmojis.ashen);
    }

    @ButtonHandler(USE_CINDERBORN)
    public static void resolveCinderborn(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Planet planet = game.getUnitHolderFromPlanet(buttonID.replace(USE_CINDERBORN, ""));
        if (planet == null || !player.getPlanets().contains(planet.getName())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (player.getNombox().getUnitCount(UnitType.Infantry, player) < 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged()
                            + " has no captured infantry available to place with CINDERBORN II.");
            return;
        }

        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), "1 infantry");
        AddUnitService.addUnits(
                event,
                game.getTileFromPlanet(planet.getName()),
                game,
                player.getColor(),
                "1 infantry " + planet.getName());

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation()
                        + " removed 1 captured "
                        + UnitEmojis.infantry
                        + " and placed it on "
                        + Helper.getPlanetRepresentationNoResInf(planet.getName(), game)
                        + " using CINDERBORN II.");
    }
}
