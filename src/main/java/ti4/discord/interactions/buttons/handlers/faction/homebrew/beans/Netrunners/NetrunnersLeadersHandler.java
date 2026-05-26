package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Netrunners;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class NetrunnersLeadersHandler {

    private static final String AGENT_ID = "netrunnersagent";
    private static final String AGENT_SPENT_PREFIX = "netrunnersagent";
    private static final String AGENT_BUTTON_PREFIX = "netrunnersAgent_";
    private static final String CHOOSE_TARGET_BUTTON_ID = "netrunnersAgentChooseTarget";
    private static final String TARGET_PICKER_MESSAGE = "choose a player to use **Overclock** on.";

    public static List<Button> getOverclockButtons(Game game, Player producingPlayer, Tile productionTile) {
        if (producingPlayer == null
                || productionTile == null
                || getHighestResourceOverclockPlanet(game, producingPlayer, productionTile) == null) {
            return List.of();
        }
        return game.getRealPlayers().stream()
                .filter(netrunner -> netrunner.hasUnexhaustedLeader(AGENT_ID))
                .map(netrunner -> Buttons.gray(
                        getOverclockButtonID(netrunner, producingPlayer, productionTile),
                        "Use Overclock",
                        FactionEmojis.netrunners))
                .toList();
    }

    public static Button getOverclockCardsInfoButton(Player netrunner) {
        return Buttons.gray(
                netrunner.factionButtonChecker() + CHOOSE_TARGET_BUTTON_ID,
                "Use Overclock on someone else",
                FactionEmojis.netrunners);
    }

    @ButtonHandler(CHOOSE_TARGET_BUTTON_ID)
    public static void offerOverclockTargetButtons(Game game, Player netrunner, ButtonInteractionEvent event) {
        List<Button> buttons = getOverclockTargetButtons(game, netrunner);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No eligible players were found for **Overclock**.");
            return;
        }

        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                event, netrunner.getRepresentationUnfogged() + ", choose a player to use **Overclock** on.", buttons);
    }

    @ButtonHandler(AGENT_BUTTON_PREFIX)
    public static void resolveOverclock(Game game, Player netrunner, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace(AGENT_BUTTON_PREFIX, "").split("_", 2);
        if (parts.length < 2) {
            return;
        }
        if (!netrunner.hasUnexhaustedLeader(AGENT_ID)) {
            rejectOverclock(event, "**Overclock** is no longer available.");
            return;
        }

        OverclockTarget target = getOverclockTarget(game, parts[0], parts[1]);
        if (target == null) {
            rejectOverclock(event, "No eligible adjacent planets were found for **Overclock**.");
            return;
        }

        if (isTargetPickerMessage(event.getMessage())) {
            resolveTargetPickerOverclock(game, netrunner, target, event);
            return;
        }

        String exhaustedMessage = applyOverclock(game, netrunner, target.producingPlayer(), target.planet());
        if (exhaustedMessage == null) {
            return;
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
        event.getMessage().editMessage(exhaustedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static String applyOverclock(Game game, Player netrunner, Player producingPlayer, Planet planet) {
        Leader agent = netrunner.getLeader(AGENT_ID).orElse(null);
        if (agent == null || !netrunner.hasUnexhaustedLeader(AGENT_ID)) {
            return null;
        }
        ExhaustLeaderService.exhaustLeader(game, netrunner, agent);
        producingPlayer.addSpentThing(AGENT_SPENT_PREFIX + "_" + planet.getResources() + "_" + netrunner.getFaction());
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                netrunner.getRepresentation() + " exhausted **Overclock** to reduce "
                        + producingPlayer.getRepresentation()
                        + "'s production cost by " + planet.getResources()
                        + ". The highest-resource eligible planet was "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + ".");
        return Helper.buildSpentThingsMessage(producingPlayer, game, "res");
    }

    private static List<Button> getOverclockTargetButtons(Game game, Player netrunner) {
        if (!netrunner.hasUnexhaustedLeader(AGENT_ID)) {
            return List.of();
        }

        return game.getRealPlayers().stream()
                .filter(producingPlayer -> producingPlayer != netrunner)
                .map(producingPlayer -> getBestOverclockTarget(game, producingPlayer))
                .filter(Objects::nonNull)
                .map(target -> Buttons.gray(
                        getOverclockButtonID(netrunner, target.producingPlayer(), target.productionTile()),
                        target.producingPlayer().getColorDisplayName() + ": "
                                + Helper.getPlanetRepresentation(target.planet().getName(), game),
                        target.producingPlayer().fogSafeEmoji()))
                .toList();
    }

    private static OverclockTarget getOverclockTarget(Game game, String faction, String position) {
        return getOverclockTarget(game, game.getPlayerFromColorOrFaction(faction), game.getTileByPosition(position));
    }

    private static OverclockTarget getBestOverclockTarget(Game game, Player producingPlayer) {
        return producingPlayer.getCurrentProducedUnits().keySet().stream()
                .map(producedUnit -> getProductionTile(game, producedUnit))
                .filter(Objects::nonNull)
                .map(productionTile -> getOverclockTarget(game, producingPlayer, productionTile))
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(target -> target.planet().getResources()))
                .orElse(null);
    }

    private static OverclockTarget getOverclockTarget(Game game, Player producingPlayer, Tile productionTile) {
        Planet planet = getHighestResourceOverclockPlanet(game, producingPlayer, productionTile);
        return planet == null ? null : new OverclockTarget(producingPlayer, productionTile, planet);
    }

    private static Tile getProductionTile(Game game, String producedUnit) {
        String[] parts = producedUnit.split("_");
        return parts.length < 2 ? null : game.getTileByPosition(parts[1]);
    }

    public static boolean isOverclockSpentThing(String thing) {
        return thing.startsWith(AGENT_SPENT_PREFIX + "_");
    }

    public static int getOverclockSpentResources(String thing) {
        return Integer.parseInt(thing.split("_")[1]);
    }

    public static String getOverclockSpentMessage(Game game, String thing) {
        String[] parts = thing.split("_");
        int discount = Integer.parseInt(parts[1]);
        String faction = game.isFowMode() ? "someone" : parts[2];
        Player netrunner = game.getPlayerFromColorOrFaction(faction);
        String source = "someone".equals(faction) || netrunner == null
                ? ""
                : " (from " + netrunner.getRepresentationNoPing() + ")";
        return "> Used **Overclock**" + source + " for " + discount + " resource" + (discount == 1 ? "" : "s") + "\n";
    }

    private static boolean isTargetPickerMessage(Message message) {
        return message.getContentRaw().contains(TARGET_PICKER_MESSAGE);
    }

    private static void resolveTargetPickerOverclock(
            Game game, Player netrunner, OverclockTarget target, ButtonInteractionEvent event) {
        String overclockButtonID = getOverclockButtonID(netrunner, target.producingPlayer(), target.productionTile());
        target.producingPlayer()
                .getCorrectChannel()
                .getHistory()
                .retrievePast(25)
                .queue(
                        messages -> {
                            Message message = messages.stream()
                                    .filter(message_ -> hasButton(message_, overclockButtonID))
                                    .findFirst()
                                    .orElse(null);
                            if (message != null) {
                                applyOverclockToPaymentMessage(
                                        game, netrunner, target, event, message, overclockButtonID);
                                return;
                            }
                            deleteWithEphemeral(
                                    event, "Could not find the active production payment message for **Overclock**.");
                            BotLogger.warning("Could not find Overclock production payment message for "
                                    + target.producingPlayer().getFaction() + ".");
                        },
                        BotLogger::catchRestError);
    }

    private static void applyOverclockToPaymentMessage(
            Game game,
            Player netrunner,
            OverclockTarget target,
            ButtonInteractionEvent event,
            Message message,
            String overclockButtonID) {
        String exhaustedMessage = applyOverclock(game, netrunner, target.producingPlayer(), target.planet());
        if (exhaustedMessage == null) {
            deleteWithEphemeral(event, "**Overclock** is no longer available.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        editSpentSummary(message, overclockButtonID, exhaustedMessage);
    }

    private static void rejectOverclock(ButtonInteractionEvent event, String message) {
        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
        removeOverclockButton(event);
    }

    private static void deleteWithEphemeral(ButtonInteractionEvent event, String message) {
        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
        ButtonHelper.deleteMessage(event);
    }

    private static void removeOverclockButton(ButtonInteractionEvent event) {
        if (isTargetPickerMessage(event.getMessage())) {
            ButtonHelper.deleteMessage(event);
        } else {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
        }
    }

    private static String getOverclockButtonID(Player netrunner, Player producingPlayer, Tile productionTile) {
        return netrunner.factionButtonChecker() + AGENT_BUTTON_PREFIX + producingPlayer.getFaction() + "_"
                + productionTile.getPosition();
    }

    private static boolean hasButton(Message message, String buttonID) {
        return message.getComponentTree().findAll(Button.class).stream()
                .map(Button::getCustomId)
                .anyMatch(buttonID::equals);
    }

    private static void editSpentSummary(Message message, String buttonID, String exhaustedMessage) {
        List<Button> buttons = message.getComponentTree().findAll(Button.class).stream()
                .filter(button -> !buttonID.equals(button.getCustomId()))
                .toList();
        message.editMessage(exhaustedMessage)
                .setComponents(ActionRow.partitionOf(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static Planet getHighestResourceOverclockPlanet(Game game, Player producingPlayer, Tile productionTile) {
        if (producingPlayer == null || productionTile == null) {
            return null;
        }

        return FoWHelper.getAdjacentTiles(game, productionTile.getPosition(), producingPlayer, false, true).stream()
                .map(game::getTileByPosition)
                .filter(Objects::nonNull)
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .filter(Planet.class::isInstance)
                .map(Planet.class::cast)
                .filter(planet -> isEligibleOverclockPlanet(game, producingPlayer, productionTile, planet))
                .max(Comparator.comparingInt(Planet::getResources))
                .orElse(null);
    }

    private static boolean isEligibleOverclockPlanet(
            Game game, Player producingPlayer, Tile productionTile, Planet planet) {
        Tile planetTile = game.getTileFromPlanet(planet.getName());
        return planet.getResources() >= 1
                && planetTile != null
                && FoWHelper.getAdjacentTiles(game, productionTile.getPosition(), producingPlayer, false, true)
                        .contains(planetTile.getPosition())
                && game.getRealPlayersExcludingThis(producingPlayer).stream()
                        .anyMatch(player -> player.getPlanets().contains(planet.getName()));
    }

    private record OverclockTarget(Player producingPlayer, Tile productionTile, Planet planet) {}
}
