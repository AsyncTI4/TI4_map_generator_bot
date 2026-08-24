package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.explore.ExploreService;

@UtilityClass
public class ExplorationRiderLLButtonHandler {
    private static final String SELECT = "resolveLostLegaciesExplorationRider_";
    private static final List<String> TRAITS = List.of("cultural", "hazardous", "industrial");

    public static void offerReward(Game game, Player player) {
        sendTraitButtons(game, player, player.getCorrectChannel(), Set.of());
    }

    @ButtonHandler(SELECT)
    public static void selectPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT.length()).split("\\|", 3);
        if (payload.length < 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Set<String> completedTraits = decodeTraits(payload[0]);
        List<Button> buttons = getTraitButtons(game, player, completedTraits);
        String message = getMessage(player, completedTraits);
        String prefix = player.factionButtonChecker() + SELECT + payload[0] + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }
        if (payload.length != 3 || !TRAITS.contains(payload[1])) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String trait = payload[1];
        String planetName = payload[2];
        Planet planet = game.getPlanetsInfo().get(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (completedTraits.contains(trait)
                || planet == null
                || tile == null
                || !player.hasPlanet(planetName)
                || !planet.getPlanetTypes().contains(trait)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That planet is no longer eligible for _Exploration Rider_.");
            return;
        }

        completedTraits.add(trait);
        ButtonHelper.deleteMessage(event);
        ExploreService.explorePlanet(event, tile, planetName, trait, player, false, game, 1, false);
        sendTraitButtons(game, player, event.getMessageChannel(), completedTraits);
    }

    private static void sendTraitButtons(
            Game game, Player player, MessageChannel channel, Set<String> completedTraits) {
        List<Button> buttons = getTraitButtons(game, player, completedTraits);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    channel, player.getRepresentationNoPing() + " finished resolving _Exploration Rider_.");
            return;
        }
        String encodedTraits = encodeTraits(completedTraits);
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                getMessage(player, completedTraits),
                NewStuffHelper.buttonPagination(
                        buttons, player.factionButtonChecker() + SELECT + encodedTraits + "|", 0));
    }

    private static List<Button> getTraitButtons(Game game, Player player, Set<String> completedTraits) {
        String encodedTraits = encodeTraits(completedTraits);
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getPlanetsInfo().get(planetName);
            if (planet == null || game.getTileFromPlanet(planetName) == null) continue;
            for (String trait : TRAITS) {
                if (completedTraits.contains(trait) || !planet.getPlanetTypes().contains(trait)) continue;
                String label = "Explore " + Helper.getPlanetRepresentation(planetName, game);
                if (planet.getPlanetTypes().size() > 1) label += " as " + trait;
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + SELECT + encodedTraits + "|" + trait + "|" + planetName,
                        label,
                        ExploreEmojis.getTraitEmoji(trait)));
            }
        }
        return buttons;
    }

    private static String getMessage(Player player, Set<String> completedTraits) {
        return player.getRepresentationNoPing()
                + ", choose a planet to explore for each remaining trait with _Exploration Rider_."
                + (completedTraits.isEmpty() ? "" : " Completed: " + String.join(", ", completedTraits) + ".");
    }

    private static String encodeTraits(Set<String> traits) {
        return traits.isEmpty()
                ? "-"
                : String.join(",", TRAITS.stream().filter(traits::contains).toList());
    }

    private static Set<String> decodeTraits(String encodedTraits) {
        return "-".equals(encodedTraits) || encodedTraits.isBlank()
                ? new HashSet<>()
                : new HashSet<>(List.of(encodedTraits.split(",")));
    }
}
