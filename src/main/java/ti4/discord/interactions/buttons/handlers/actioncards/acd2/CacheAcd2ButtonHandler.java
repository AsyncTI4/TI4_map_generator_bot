package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class CacheAcd2ButtonHandler {

    @ButtonHandler("resolveCache")
    public static void resolveCache(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = player.getReadiedPlanets().stream()
                .map(planet -> game.getPlanetsInfo().get(planet))
                .filter(Objects::nonNull)
                .filter(planet -> !planet.isHomePlanet(game))
                .map(planet -> Buttons.green(
                        "resolveCacheStep2_" + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game) + " ("
                                + planet.getSumResourcesInfluence() + " TG)"))
                .toList();

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no ready non-home planets to exhaust for _Cache_.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose a non-home planet to exhaust for _Cache_.",
                buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveCacheStep2_")
    public static void resolveCacheStep2(Player player, Game game, String buttonID) {
        String planetName = buttonID.split("_")[1];
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (planet == null || !player.hasPlanet(planetName) || planet.isHomePlanet(game)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve _Cache_ for that planet.");
            return;
        }
        if (!player.hasPlanetReady(planetName)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    Helper.getPlanetRepresentation(planetName, game) + " is already exhausted.");
            return;
        }

        int tgGain = planet.getSumResourcesInfluence();
        player.exhaustPlanet(planetName);
        player.gainTG(tgGain);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, tgGain);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " exhausted "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game)
                        + " and gained " + StringHelper.pluralize(tgGain, "trade good") + " from _Cache_.");
    }
}
