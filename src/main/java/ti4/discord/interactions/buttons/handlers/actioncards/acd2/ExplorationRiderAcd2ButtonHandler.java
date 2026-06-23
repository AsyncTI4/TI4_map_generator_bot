package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperActionCards;
import ti4.message.MessageHelper;
import ti4.service.explore.ExploreService;

@UtilityClass
class ExplorationRiderAcd2ButtonHandler {

    @ButtonHandler("resolveExplorationRider")
    public static void resolveExplorationRider(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        ButtonHelperActionCards.sendExplorationRiderButtons(player, game, 3, Set.of());
    }

    @ButtonHandler("resolveExplorationRiderStep2_")
    public static void resolveExplorationRiderStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("resolveExplorationRiderStep2_", "");
        int firstSeparator = payload.indexOf('_');
        int secondSeparator = payload.indexOf('_', firstSeparator + 1);
        int lastSeparator = payload.lastIndexOf('_');
        if (firstSeparator < 0 || secondSeparator < 0 || lastSeparator <= secondSeparator) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Exploration Rider_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        int remainingExplores;
        try {
            remainingExplores = Integer.parseInt(payload.substring(0, firstSeparator));
        } catch (NumberFormatException e) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Exploration Rider_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Set<String> selectedPlanets = ButtonHelperActionCards.decodeExplorationRiderPlanets(
                payload.substring(firstSeparator + 1, secondSeparator));
        String planet = payload.substring(secondSeparator + 1, lastSeparator);
        String trait = payload.substring(lastSeparator + 1);
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null
                || !ButtonHelperActionCards.isExplorationRiderEligiblePlanet(player, game, planet, selectedPlanets)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Exploration Rider_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        selectedPlanets.add(planet);
        ButtonHelper.deleteMessage(event);
        ExploreService.explorePlanet(event, tile, planet, trait, player, false, game, 1, false);
        if (remainingExplores > 1) {
            ButtonHelperActionCards.sendExplorationRiderButtons(player, game, remainingExplores - 1, selectedPlanets);
        }
    }

    @ButtonHandler("resolveExplorationRiderDrawAc_")
    public static void resolveExplorationRiderDrawAc(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("resolveExplorationRiderDrawAc_", "");
        int separator = payload.indexOf('_');
        if (separator < 0) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Exploration Rider_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        int remainingExplores;
        try {
            remainingExplores = Integer.parseInt(payload.substring(0, separator));
        } catch (NumberFormatException e) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Exploration Rider_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Set<String> selectedPlanets =
                ButtonHelperActionCards.decodeExplorationRiderPlanets(payload.substring(separator + 1));
        ActionCardHelper.drawActionCards(player, 1);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " drew 1 action card instead of exploring with _Exploration Rider_ via breakthrough.");
        if (remainingExplores > 1) {
            ButtonHelperActionCards.sendExplorationRiderButtons(player, game, remainingExplores - 1, selectedPlanets);
        }
    }
}
