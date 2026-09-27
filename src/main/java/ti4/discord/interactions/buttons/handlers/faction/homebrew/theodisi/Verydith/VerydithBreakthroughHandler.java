package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Verydith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
public class VerydithBreakthroughHandler {
    private static final Map<GenericInteractionCreateEvent, Set<String>> PROMPTS_BY_EVENT =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void verydithBTExhaust(GenericInteractionCreateEvent event, Game game, Player player) {
        if (player == null || game == null) {
            return;
        }

        List<Button> passedPlayers = new ArrayList<>();
        for (Player otherPlayer : game.getRealPlayersExcludingThis(player)) {
            if (!otherPlayer.isPassed()) {
                continue;
            }

            passedPlayers.add(Buttons.green(
                    player.factionButtonChecker() + "selectPassedPlayerVerydith_" + otherPlayer.getFaction(),
                    otherPlayer.getColor(),
                    otherPlayer.getFactionEmojiOrColor()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose the passed player to use _Unyielding Accord_ on.",
                passedPlayers);
    }

    @ButtonHandler("selectPassedPlayerVerydith_")
    public static void selectUnyieldingAccordSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String factionName = buttonID.replace("selectPassedPlayerVerydith_", "");
        Player targetFaction = game.getPlayerFromColorOrFaction(factionName);
        if (targetFaction == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> eligibleSystems = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!FoWHelper.playerHasUnitsInSystem(player, tile)) {
                continue;
            }
            if (tile.hasPlayerCC(targetFaction)) {
                continue;
            }

            eligibleSystems.add(Buttons.green(
                    player.factionButtonChecker() + "placeCCInSystemVerydith_" + targetFaction.getFaction() + "|"
                            + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", choose the system to place " + targetFaction.getRepresentationNoPing()
                        + "'s command token.",
                eligibleSystems);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("placeCCInSystemVerydith_")
    public static void resolveVerydithBTExhaust(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String payload = buttonID.substring("placeCCInSystemVerydith_".length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            return;
        }

        String target = parts[0];
        String tilePos = parts[1];
        Player targetPlayer = game.getPlayerFromColorOrFaction(target);
        Tile tile = game.getTileByPosition(tilePos);
        if (targetPlayer == null || tile == null) {
            return;
        }

        String ccID = Mapper.getCCID(targetPlayer.getColor());

        tile.addCC(ccID);

        offerUnyieldingAccord(event, player, tile);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " added " + targetPlayer.getRepresentationNoPing()
                        + "'s command token to " + tile.getRepresentation() + ".");

        ButtonHelper.deleteMessage(event);
    }

    public static void offerUnyieldingAccord(GenericInteractionCreateEvent event, Player player, Tile tile) {
        if (player == null || tile == null || !player.hasUnlockedBreakthrough("verydithbt")) {
            return;
        }

        if (event != null) {
            String promptKey = player.getFaction() + "|" + tile.getPosition();
            synchronized (PROMPTS_BY_EVENT) {
                if (!PROMPTS_BY_EVENT
                        .computeIfAbsent(event, ignored -> new HashSet<>())
                        .add(promptKey)) {
                    return;
                }
            }
        }

        List<Button> buttons = new ArrayList<>();

        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (!player.hasPlanet(planet.getName())) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "verydithBt_" + planet.getName(),
                    "Ready and Explore " + Helper.getPlanetRepresentation(planet.getName(), player.getGame())));
        }

        if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", another player's command token was placed in a system with your planets. Please choose 1 planet to ready and explore with _Unyielding Accord_.",
                    buttons);
        }
    }

    @ButtonHandler("verydithBt_")
    public static void resolveUnyieldingAccord(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String planetName = buttonID.replace("verydithBt_", "");
        Planet planet = game.getPlanetsInfo().get(planetName);

        player.refreshPlanet(planetName);

        List<Button> exploreButtons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
        String message = player.getRepresentation() + " readied " + Helper.getPlanetRepresentation(planetName, game)
                + " due to _Unyielding Accord_.";
        if (exploreButtons == null || exploreButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(), message + " You may now explore it.", exploreButtons);
        }

        ButtonHelper.deleteMessage(event);
    }
}
