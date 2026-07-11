package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
class DefenseInstallationAcd2ButtonHandler {

    @ButtonHandler("resolveDefenseInstallation")
    public static void resolveDefenseInstallation(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = player.getPlanets().stream()
                .map(planet -> Buttons.green(
                        "defenseInstallationStep2_" + planet, Helper.getPlanetRepresentation(planet, game)))
                .toList();
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the planet you wish to put 1 PDS on.",
                buttons);
    }

    @ButtonHandler("defenseInstallationStep2_")
    public static void resolveDefenseInstallationStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        AddUnitService.addUnits(event, game.getTileContainingPlanet(planet), game, player.getColor(), "pds " + planet);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " put 1 PDS on " + Helper.getPlanetRepresentation(planet, game)
                        + ".");
    }
}
