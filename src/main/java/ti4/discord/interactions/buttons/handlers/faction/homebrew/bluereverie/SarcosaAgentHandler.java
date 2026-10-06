package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class SarcosaAgentHandler {
    private static final String SELECT_SYSTEM = "selectSarcosaAgentSystem_";
    private static final String PLACE_UNITS = "placeSarcosaAgentUnits_";

    public static void offerSarcosaAgentSystemButtons(Game game, Player target, GenericInteractionCreateEvent event) {
        List<Button> buttons = getEligibleSystemButtons(game, target);
        String message = target.getRepresentation() + ", choose a system in or adjacent to one containing your units.";
        String prefix = target.factionButtonChecker() + SELECT_SYSTEM;
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(), target.getRepresentationNoPing() + " has no eligible systems.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(), message, NewStuffHelper.buttonPagination(buttons, prefix, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_SYSTEM)
    public static void selectSarcosaAgentSystem(
            ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        List<Button> systemButtons = getEligibleSystemButtons(game, target);
        String message = target.getRepresentation() + ", choose a system in or adjacent to one containing your units.";
        String prefix = target.factionButtonChecker() + SELECT_SYSTEM;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), systemButtons, message, prefix, buttonID)) return;
        Tile tile = game.getTileByPosition(buttonID.substring(SELECT_SYSTEM.length()));
        if (!isEligibleSystem(game, target, tile)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That system is no longer eligible.");
            return;
        }
        List<Button> unitButtons = List.of(
                Buttons.gray(
                        target.factionButtonChecker() + PLACE_UNITS + tile.getPosition() + "|1dd",
                        "Place 1 Neutral Destroyer",
                        FactionEmojis.sarcosa),
                Buttons.gray(
                        target.factionButtonChecker() + PLACE_UNITS + tile.getPosition() + "|2dd",
                        "Place 2 Neutral Destroyers",
                        FactionEmojis.sarcosa),
                Buttons.gray(
                        target.factionButtonChecker() + PLACE_UNITS + tile.getPosition() + "|ca",
                        "Place 1 Neutral Cruiser",
                        FactionEmojis.sarcosa));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentation() + ", choose the neutral ships to place.",
                unitButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_UNITS)
    public static void placeSarcosaAgentUnits(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] values = buttonID.substring(PLACE_UNITS.length()).split("\\|", 2);
        Tile tile = values.length == 2 ? game.getTileByPosition(values[0]) : null;
        if (tile == null || !isEligibleSystem(game, target, tile)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That system is no longer eligible.");
            return;
        }
        String units =
                switch (values[1]) {
                    case "1dd" -> "1 dd";
                    case "2dd" -> "2 dd";
                    case "ca" -> "1 ca";
                    default -> "";
                };
        if (units.isEmpty()) return;
        AddUnitService.addUnits(event, tile, game, game.getNeutralColor(), units);
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + " placed neutral ships in "
                        + tile.getRepresentationForButtons(game, target) + " with Drift-Yol Khas.");
        StartCombatService.combatCheck(game, event, tile);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getEligibleSystemButtons(Game game, Player target) {
        return game.getTileMap().values().stream()
                .filter(tile -> isEligibleSystem(game, target, tile))
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.gray(
                        target.factionButtonChecker() + SELECT_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, target),
                        FactionEmojis.sarcosa))
                .toList();
    }

    private static boolean isEligibleSystem(Game game, Player target, Tile destination) {
        return destination != null
                && !destination.getTileModel().isHyperlane()
                && game.getTileMap().values().stream()
                        .anyMatch(source -> FoWHelper.playerHasUnitsInSystem(target, source)
                                && (source == destination
                                        || FoWHelper.getAdjacentTiles(game, source.getPosition(), target, false)
                                                .contains(destination.getPosition())));
    }
}
