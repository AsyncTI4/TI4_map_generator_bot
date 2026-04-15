package ti4.discord.interactions.buttons.handlers.planet;

import static org.apache.commons.lang3.StringUtils.countMatches;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.commands.planet.PlanetExhaustAbility;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.service.planet.PlanetService;

@UtilityClass
class PlanetExhaustButtonHandler {

    @ButtonHandler("planetAbilityExhaust_")
    public static void planetAbilityExhaust(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.replace("planetAbilityExhaust_", "");
        PlanetExhaustAbility.doAction(event, player, planet, game, true);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("refresh_")
    public static void refresh(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planetName = buttonID.split("_")[1];
        Player p2 = player;
        if (countMatches(buttonID, "_") > 1) {
            String faction = buttonID.split("_")[2];
            p2 = game.getPlayerFromColorOrFaction(faction);
        }

        PlanetService.refreshPlanet(p2, planetName);
        String totalVotesSoFar = event.getMessage().getContentRaw();
        if (totalVotesSoFar.contains("Readied")) {
            totalVotesSoFar += ", " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        } else {
            totalVotesSoFar = player.getFactionEmojiOrColor() + " Readied "
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        }
        event.getMessage().editMessage(totalVotesSoFar).queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
