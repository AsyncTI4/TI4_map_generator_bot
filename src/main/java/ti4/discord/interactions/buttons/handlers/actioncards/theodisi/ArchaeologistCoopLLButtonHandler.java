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
public class ArchaeologistCoopLLButtonHandler {
    private static final String RESOLVE = "resolveArchaeologistCoop";
    private static final String SOURCE = "resolveArchaeologistCoopSource_";
    private static final String EXPLORE = "resolveArchaeologistCoopExplore_";
    private static final String STATE = "archaeologistCoop_";

    @ButtonHandler(RESOLVE)
    public static void resolveArchaeologistCoop(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getSourceButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " has no system with another controlled planet to explore for _Archaeologist Co-op_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String message = player.getRepresentationNoPing()
                + ", choose the planet just explored for _Archaeologist Co-op_. The bot will then offer the other controlled planets in that system.";
        MessageHelper.editMessageWithButtons(
                event, message, NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + SOURCE, 0));
    }

    @ButtonHandler(SOURCE)
    public static void selectSource(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getSourceButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose the planet just explored for _Archaeologist Co-op_. The bot will then offer the other controlled planets in that system.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, player.factionButtonChecker() + SOURCE, buttonID)) {
            return;
        }

        String sourcePlanet = buttonID.substring(SOURCE.length());
        Tile tile = game.getTileFromPlanet(sourcePlanet);
        if (tile == null || !getSourcePlanets(game, player).contains(sourcePlanet)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That planet is no longer eligible for _Archaeologist Co-op_.");
            return;
        }
        game.setStoredValue(STATE + player.getFaction(), tile.getPosition() + "|" + sourcePlanet);
        ButtonHelper.deleteMessage(event);
        sendExploreButtons(game, player, event.getMessageChannel());
    }

    @ButtonHandler(EXPLORE)
    public static void explorePlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] state = game.getStoredValue(STATE + player.getFaction()).split("\\|", 3);
        if (state.length < 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "This _Archaeologist Co-op_ selection has expired.");
            return;
        }
        List<Button> buttons = getExploreButtons(game, player, state[0], state[1], getExploredPlanets(game, player));
        String message = player.getRepresentationNoPing()
                + ", explore each other controlled planet in this system for _Archaeologist Co-op_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + EXPLORE,
                buttonID)) {
            return;
        }

        String[] payload = buttonID.substring(EXPLORE.length()).split("\\|", 2);
        if (payload.length != 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "This _Archaeologist Co-op_ selection has expired.");
            return;
        }

        String planetName = payload[0];
        String trait = payload[1];
        Tile tile = game.getTileByPosition(state[0]);
        Planet planet = game.getPlanetsInfo().get(planetName);
        Set<String> exploredPlanets = getExploredPlanets(game, player);
        if (tile == null
                || planet == null
                || planetName.equals(state[1])
                || exploredPlanets.contains(planetName)
                || !player.hasPlanet(planetName)
                || tile.getPlanetUnitHolders().stream().noneMatch(holder -> planetName.equals(holder.getName()))
                || !planet.getPlanetTypes().contains(trait)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That planet is no longer eligible for _Archaeologist Co-op_.");
            return;
        }

        exploredPlanets.add(planetName);
        game.setStoredValue(
                STATE + player.getFaction(), state[0] + "|" + state[1] + "|" + String.join(",", exploredPlanets));
        ButtonHelper.deleteMessage(event);
        ExploreService.explorePlanet(event, tile, planetName, trait, player, false, game, 1, false);
        sendExploreButtons(game, player, event.getMessageChannel());
    }

    private static List<Button> getSourceButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planetName : getSourcePlanets(game, player)) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SOURCE + planetName,
                    "Explored " + Helper.getPlanetRepresentation(planetName, game)));
        }
        return buttons;
    }

    private static Set<String> getSourcePlanets(Game game, Player player) {
        Set<String> sourcePlanets = new HashSet<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getPlanetsInfo().get(planetName);
            Tile tile = game.getTileFromPlanet(planetName);
            if (planet == null || tile == null || planet.getPlanetTypes().isEmpty()) continue;
            long eligibleOtherPlanets = tile.getPlanetUnitHolders().stream()
                    .map(Planet::getName)
                    .filter(otherPlanet -> !otherPlanet.equals(planetName))
                    .filter(player::hasPlanet)
                    .map(game.getPlanetsInfo()::get)
                    .filter(otherPlanet ->
                            otherPlanet != null && !otherPlanet.getPlanetTypes().isEmpty())
                    .count();
            if (eligibleOtherPlanets > 0) sourcePlanets.add(planetName);
        }
        return sourcePlanets;
    }

    private static void sendExploreButtons(Game game, Player player, MessageChannel channel) {
        String[] state = game.getStoredValue(STATE + player.getFaction()).split("\\|", 3);
        if (state.length < 2) return;
        List<Button> buttons = getExploreButtons(game, player, state[0], state[1], getExploredPlanets(game, player));
        if (buttons.isEmpty()) {
            game.removeStoredValue(STATE + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    channel, player.getRepresentationNoPing() + " finished resolving _Archaeologist Co-op_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                player.getRepresentationNoPing()
                        + ", explore each other controlled planet in this system for _Archaeologist Co-op_.",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + EXPLORE, 0));
    }

    private static List<Button> getExploreButtons(
            Game game, Player player, String tilePosition, String sourcePlanet, Set<String> exploredPlanets) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) return buttons;
        for (Planet planet : tile.getPlanetUnitHolders()) {
            String planetName = planet.getName();
            if (planetName.equals(sourcePlanet)
                    || exploredPlanets.contains(planetName)
                    || !player.hasPlanet(planetName)) {
                continue;
            }
            Set<String> traits = planet.getPlanetTypes();
            for (String trait : traits) {
                String label = "Explore " + Helper.getPlanetRepresentation(planetName, game);
                if (traits.size() > 1) label += " as " + trait;
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + EXPLORE + planetName + "|" + trait,
                        label,
                        ExploreEmojis.getTraitEmoji(trait)));
            }
        }
        return buttons;
    }

    private static Set<String> getExploredPlanets(Game game, Player player) {
        String[] state = game.getStoredValue(STATE + player.getFaction()).split("\\|", 3);
        if (state.length < 3 || state[2].isBlank()) return new HashSet<>();
        return new HashSet<>(List.of(state[2].split(",")));
    }
}
