package ti4.helpers.thundersedge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.ActionCardModel;
import ti4.model.UnitModel;
import ti4.service.RemoveCommandCounterService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.regex.RegexService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public class TeHelperAgents {

    public static boolean handleTeAgentExhaust(Game game, Player player, String leaderID, String targetPlayer) {
        Player target = game.getPlayerFromColorOrFaction(targetPlayer);
        if (target == null) target = player;
        switch (leaderID) {
            case "crimsonagent" -> postCrimsonAgentStep1(game, target);
            case "ralnelagent" -> postRalNelAgentStep1(game, target);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static void postRalNelAgentStep1(Game game, Player player) {
        Map<String, Integer> acsBefore = new HashMap<>(player.getActionCards());
        ActionCardHelper.drawActionCards(game, player, 2, true);
        Map<String, Integer> acsAfter = new HashMap<>(player.getActionCards());

        List<Button> buttons = new ArrayList<>();
        List<String> newACs = acsAfter.keySet().stream()
                .filter(key -> !acsBefore.containsKey(key))
                .toList();
        for (String ac : newACs) {
            ActionCardModel model = Mapper.getActionCard(ac);
            buttons.add(Buttons.red("handleRalNelAgent_" + ac, model.getName(), CardEmojis.ActionCard));
        }

        String msg = player.getRepresentation(true, true)
                + ", please choose the action card you wish to send to another another player.";
        if (player.getActionCards().size() > ButtonHelper.getACLimit(game, player))
            msg += "\nNB: This happens __before__ discarding down to hand limit.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("handleRalNelAgent_")
    private static void handleRalNelAgent(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String part1 = "handleRalNelAgent_" + RegexHelper.acRegex(game);
        RegexService.runMatcher(part1, buttonID, matcher -> {
            String acID = matcher.group("ac");
            String msg = player.getRepresentation(true, true) + ", please choose the player you wish to give _"
                    + Mapper.getActionCard(acID).getName() + "_ to.";
            List<Button> buttons = new ArrayList<>();
            String prefix = "handleRalNelAgentPt2_" + acID + "_";
            for (Player p2 : game.getRealPlayers()) {
                String label = game.isFowMode() ? p2.getColor() : p2.getFlexibleDisplayName();
                String emoji = game.isFowMode()
                        ? ColorEmojis.getColorEmoji(p2.getColor()).toString()
                        : p2.getFactionEmoji();
                buttons.add(Buttons.gray(prefix + p2.getFaction(), label, emoji));
            }
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("handleRalNelAgentPt2_")
    private static void handleRalNelAgentPart2(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "handleRalNelAgentPt2_" + RegexHelper.acRegex(game) + "_" + RegexHelper.factionRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String acID = matcher.group("ac");
            String faction = matcher.group("faction");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 != null) {
                ActionCardHelper.sendActionCard(event, game, player, game.getPlayerFromColorOrFaction(faction), acID);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation(true, true) + " sent an action card to " + p2.getRepresentation()
                                + ".");
                if (game.isFowMode())
                    MessageHelper.sendMessageToChannel(
                            p2.getCorrectChannel(),
                            (game.isFowMode() ? player.getColorIfCanSeeStats(p2) : player.getRepresentation())
                                    + " sent an action card to " + p2.getRepresentation(true, true) + ".");
            } else {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Error, player2 is null");
            }
            ButtonHelper.deleteMessage(event);
        });
    }

    private static void postCrimsonAgentStep1(Game game, Player player) {
        Predicate<Tile> pred = t -> t.containsPlayersUnitsWithModelCondition(player, UnitModel::getIsShip);
        String message =
                player.getRepresentation() + ", please choose the first system from which you wish to swap a ship.";
        List<Button> buttons =
                ButtonHelper.getTilesWithPredicateForAction(player, game, "handleCrimsonAgent", pred, false);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("handleCrimsonAgent_")
    private static void handleCrimsonAgent(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String prefix = buttonID + "_";
        Predicate<Tile> pred = t -> t.containsPlayersUnitsWithModelCondition(player, UnitModel::getIsShip);

        String part1 = "handleCrimsonAgent";
        String part2 = part1 + "_" + RegexHelper.posRegex(game, "tileA");
        String part3 = part2 + "_" + RegexHelper.unitTypeRegex("unitA");
        String part4 = part3 + "_" + RegexHelper.posRegex(game, "tileB");
        String part5 = part4 + "_" + RegexHelper.unitTypeRegex("unitB");

        Matcher matcher;
        String newMessage = null;
        List<Button> newButtons = new ArrayList<>();
        if ((matcher = Pattern.compile(part2).matcher(buttonID)).matches()) {
            Tile from = game.getTileByPosition(matcher.group("tileA"));
            newMessage = player.getRepresentation() + ", you are swapping from "
                    + from.getRepresentationForButtons(game, player) + ". Please choose the ship you wish to swap.";

            UnitHolder space = from.getSpaceUnitHolder();
            Set<UnitKey> keys = space.getUnitsByState().keySet().stream()
                    .filter(unit -> space.getUnitCount(unit) > 0)
                    .filter(player::unitBelongsToPlayer)
                    .filter(uk -> player.getUnitFromUnitKey(uk).getIsShip())
                    .collect(Collectors.toSet());
            keys.stream()
                    .map(uk ->
                            Buttons.blue(prefix + uk.asyncID(), uk.getUnitType().humanReadableName(), uk.unitEmoji()))
                    .forEach(newButtons::add);

        } else if ((matcher = Pattern.compile(part3).matcher(buttonID)).matches()) {
            Tile from = game.getTileByPosition(matcher.group("tileA"));
            UnitType unitType = Units.findUnitType(matcher.group("unitA"));

            newMessage = player.getRepresentation() + ", you are swapping a " + unitType.humanReadableName() + " from "
                    + from.getRepresentationForButtons(game, player) + ".";
            newMessage += "\nPlease choose the other system from which you wish to swap a ship.";
            newButtons = ButtonHelper.getTilesWithPredicateForAction(player, game, buttonID, pred, false);

        } else if ((matcher = Pattern.compile(part4).matcher(buttonID)).matches()) {
            Tile from = game.getTileByPosition(matcher.group("tileA"));
            UnitType unitType = Units.findUnitType(matcher.group("unitA"));
            Tile to = game.getTileByPosition(matcher.group("tileB"));

            newMessage = player.getRepresentation() + " you are swapping a " + unitType.humanReadableName() + " from "
                    + from.getRepresentationForButtons(game, player) + ".";
            newMessage += "\nYou are swapping to " + to.getRepresentationForButtons(game, player)
                    + ". Please choose the ship you wish to swap.";

            UnitHolder space = to.getSpaceUnitHolder();
            Set<UnitKey> keys = space.getUnitsByState().keySet().stream()
                    .filter(unit -> space.getUnitCount(unit) > 0)
                    .filter(player::unitBelongsToPlayer)
                    .filter(uk -> player.getUnitFromUnitKey(uk).getIsShip())
                    .collect(Collectors.toSet());
            keys.stream()
                    .map(uk ->
                            Buttons.blue(prefix + uk.asyncID(), uk.getUnitType().humanReadableName(), uk.unitEmoji()))
                    .forEach(newButtons::add);

        } else if ((matcher = Pattern.compile(part5).matcher(buttonID)).matches()) {
            Tile tileA = game.getTileByPosition(matcher.group("tileA"));
            Tile tileB = game.getTileByPosition(matcher.group("tileB"));
            String unitA = matcher.group("unitA");
            String unitB = matcher.group("unitB");
            UnitType unitTypeA = Units.findUnitType(unitA);
            UnitType unitTypeB = Units.findUnitType(unitB);

            // remove the units
            RemoveUnitService.removeUnits(event, tileA, game, player.getColor(), unitA);
            RemoveUnitService.removeUnits(event, tileB, game, player.getColor(), unitB);

            // add the units
            AddUnitService.addUnits(event, tileB, game, player.getColor(), unitA);
            AddUnitService.addUnits(event, tileA, game, player.getColor(), unitB);

            String message = player.getRepresentation() + " swapped two ships using Ahk Ravin.";
            message +=
                    "\n> " + unitTypeA.humanReadableName() + " at " + tileA.getRepresentationForButtons(game, player);
            message +=
                    "\n> " + unitTypeB.humanReadableName() + " at " + tileB.getRepresentationForButtons(game, player);

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.deleteMessage(event);
        }

        if (newMessage != null) {
            // edit the message with the new partX buttons
            event.getMessage()
                    .editMessage(newMessage)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(newButtons))
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static void serveNaaluAgentButtons(Game game, Player player, Tile tile, Player p2) {
        // Not allowed in fow if you can't see the tile
        if (game.isFowMode() && tile.hasFog(player) && p2 != player) return;
        if (!player.hasUnexhaustedLeader("naaluagent-te")) return;

        List<Button> buttons = new ArrayList<>();
        String id = "useNaaluAgent_" + tile.getPosition() + "_" + p2.getFaction();
        String label = "Remove A Token From " + tile.getRepresentationForButtons(game, player);
        buttons.add(Buttons.green(id, label, LeaderEmojis.NaaluAgent));
        buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("No Thanks"));

        String msg = " You may use " + Mapper.getLeader("naaluagent-te").getNameRepresentation();
        msg += " to remove a " + p2.getColor() + " command token from " + tile.getRepresentationForButtons(game, player)
                + ".";
        if (p2 == player) {
            msg = player.getRepresentation() + msg;
        } else {
            msg = player.getRepresentationNoPing() + msg;
        }

        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("useNaaluAgent_")
    private static void resolveNaaluAgent(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "useNaaluAgent_" + RegexHelper.posRegex(game) + "_" + RegexHelper.factionRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            Player p3 = game.getPlayerFromColorOrFaction(matcher.group("faction"));

            player.getLeaderByID("naaluagent-te").ifPresent(zeu -> {
                ExhaustLeaderService.exhaustLeader(game, player, zeu);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation() + " exhausted Z'eu ΩΩ to remove the just-placed command token from "
                                + tile.getRepresentationForButtons() + ".");
                RemoveCommandCounterService.fromTile(event, p3, tile);
            });
            for (Player p2 : game.getRealPlayers()) {
                if (p2.hasTech("tcs") && !p2.getExhaustedTechs().contains("tcs")) {
                    List<Button> buttons2 = new ArrayList<>();
                    String msg;
                    if (game.isTwilightsFallMode()) {
                        buttons2.add(Buttons.green(
                                p2.getFinsFactionCheckerPrefix() + "useTCS_naaluagent-te_" + player.getFaction(),
                                "Spend A Command Token To Ready Limit Genome"));
                        buttons2.add(Buttons.red(p2.getFinsFactionCheckerPrefix() + "deleteButtons", "Decline"));
                        msg = p2.getRepresentationUnfogged()
                                + " you have the opportunity to spend a command token via _Temporal Command Suite_ to ready _Limit Genome_, and potentially resolve a transaction.";
                    } else {
                        buttons2.add(Buttons.green(
                                p2.getFinsFactionCheckerPrefix() + "exhaustTCS_naaluagent-te_" + player.getFaction(),
                                "Exhaust Temporal Command Suite to Ready Naalu Agent"));
                        buttons2.add(Buttons.red(p2.getFinsFactionCheckerPrefix() + "deleteButtons", "Decline"));
                        msg = p2.getRepresentationUnfogged()
                                + " you have the opportunity to exhaust _Temporal Command Suite_ to ready Z'eu, and potentially resolve a transaction.";
                    }
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), msg, buttons2);
                }
            }
        });
        ButtonHelper.deleteMessage(event);
    }
}
