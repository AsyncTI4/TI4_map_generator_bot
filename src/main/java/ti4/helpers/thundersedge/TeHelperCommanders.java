package ti4.helpers.thundersedge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public class TeHelperCommanders {
    @ButtonHandler("useDwsDiscount_")
    public static void useDwsTechDiscount(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String commanderFaction = buttonID.replace("useDwsDiscount_", "");
        Player dws = game.getPlayerFromColorOrFaction(commanderFaction);
        if (dws == null) return;

        // Add the discount to spent things for the player using the discount
        player.addSpentThing("dwsDiscount_" + commanderFaction);
        ButtonHelper.deleteTheOneButton(event, buttonID, false);
        String editedMsg = Helper.buildSpentThingsMessage(player, game, "res");
        event.editMessage(editedMsg).queue();

        // Notify the DWS commander holder that their discount was used
        String message = dws.getRepresentation(true, true) + " ";
        message += game.isFowMode() ? "someone" : player.getRepresentation(true, false);
        message += " has used your commander to get a discount on tech.";
        message += " Use the buttons to gain or convert 1 commodity.";
        message += "\n-# You have (" + dws.getCommoditiesRepresentation() + ") commodities.";
        List<Button> buttons = ButtonHelperFactionSpecific.gainOrConvertCommButtons(dws, true);
        MessageHelper.sendMessageToChannelWithButtons(game.getActionsChannel(), message, buttons);
    }

    public static void offerCrimsonCommanderButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        if (game.playerHasLeaderUnlockedOrAlliance(player, "crimsoncommander")) {
            String message = player.getRepresentation(true, true)
                    + " Resolve crimson commander using buttons\n> (note this is not available until the end of combat)";
            message += "\n-# You have (" + player.getCommoditiesRepresentation() + ") commodities.";
            List<Button> buttons = ButtonHelperFactionSpecific.gainOrConvertCommButtons(player, true);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        }
    }

    public static List<Button> getButtonsForRalNelRetreat(Game game, Player player) {
        String pos = game.getActiveSystem();
        Tile tile = game.getTileByPosition(pos);

        List<Button> buttons = new ArrayList<>();
        buttons = ButtonHelperTacticalAction.getButtonsForAllUnitsInSystem(player, game, tile, "Remove");
        return buttons;
    }

    @ButtonHandler("moveOjzRetreatS1_")
    @ButtonHandler("undoOjzRetreatS1_")
    private static void ojzRetreatStep1(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        HashMap<String, List<String>> ojzMap = TeHelperAbilities.readMoveMap(game.getStoredValue("OjzRetreatMap"));
        String regex = "(undo|move)OjzRetreatS1_" + RegexHelper.posRegex(game, "source") + "_"
                + RegexHelper.unitTypeRegex() + "_" + RegexHelper.unitHolderRegex(game, "planet");
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            String pos = matcher.group("source");
            Tile source = game.getTileByPosition(pos);
            String unitStr = matcher.group("unittype") + " " + matcher.group("planet");

            if (buttonID.startsWith("move")) {
                if (!ojzMap.containsKey(pos)) ojzMap.put(pos, new ArrayList<>());
                ojzMap.get(pos).add(unitStr);
            } else {
                if (ojzMap.containsKey(pos)) ojzMap.get(pos).remove(unitStr);
                if (ojzMap.containsKey(pos) && ojzMap.get(pos).isEmpty()) ojzMap.remove(pos);
            }
            game.setStoredValue("OjzRetreatMap", TeHelperAbilities.storeMovementMap(ojzMap));

            List<Button> buttons = getWatchfulOjzUnitButtons(game, player, source, ojzMap.get(pos));
            String message = player.getRepresentation()
                    + " Choose up to 2 ships to retreat as well as anything they transport using Watchful Ojz, the Ral Nel commander:";
            message += TeHelperAbilities.unitSummary(game, player, ojzMap);
            MessageHelper.editMessageWithButtons(event, message, buttons);
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Error " + Constants.jazzPing());
        }
    }

    public static List<Tile> ojzDestinations(Game game, Player player, String source) {
        Set<String> adj = FoWHelper.getAdjacentTilesAndNotThisTile(game, source, player, false);
        List<Tile> valid = new ArrayList<>();
        for (String pos : adj) {
            Tile tile = game.getTileByPosition(pos);
            if (tile == null) continue;
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) valid.add(tile);
        }
        return valid;
    }

    @ButtonHandler("ojzRetreatS2_")
    private static void ojzRetreatStep2(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        HashMap<String, List<String>> ojzMap = TeHelperAbilities.readMoveMap(game.getStoredValue("OjzRetreatMap"));
        String regex = "ojzRetreatS2_" + RegexHelper.posRegex(game, "source");
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            String pos = matcher.group("source");

            List<Button> buttons = new ArrayList<>();
            List<Tile> tiles = ojzDestinations(game, player, pos);
            for (Tile t : tiles) {
                buttons.add(Buttons.green(
                        player.finChecker() + "ojzRetreatS3_" + t.getPosition(),
                        "Retreat to " + t.getRepresentationForButtons(game, player)));
            }

            String message = player.getRepresentation() + " Choose a destination to send your retreating units:";
            message += TeHelperAbilities.unitSummary(game, player, ojzMap);
            MessageHelper.editMessageWithButtons(event, message, buttons);
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Error " + Constants.jazzPing());
        }
    }

    @ButtonHandler("ojzRetreatS3_")
    private static void ojzRetreatStep3(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "ojzRetreatS3_" + RegexHelper.posRegex();
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            boolean movedFlagship = false;
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            HashMap<String, List<String>> ojzMap = TeHelperAbilities.readMoveMap(game.getStoredValue("OjzRetreatMap"));
            for (String source : ojzMap.keySet()) {
                if (ojzMap.get(source).contains("fs space")) movedFlagship = true;
                List<String> units =
                        ojzMap.get(source).stream().collect(Collectors.groupingBy(s -> s)).entrySet().stream()
                                .map(e -> e.getValue().size() + " " + e.getKey())
                                .toList();
                List<String> unitsTo = ojzMap.get(source).stream()
                        .map(unit -> unit.substring(0, unit.indexOf(' ')))
                        .collect(Collectors.groupingBy(s -> s))
                        .entrySet()
                        .stream()
                        .map(e -> e.getValue().size() + " " + e.getKey())
                        .toList();
                String unitStrFrom = String.join(", ", units);
                String unitStrTo = String.join(", ", unitsTo);
                RemoveUnitService.removeUnits(
                        event, game.getTileByPosition(source), game, player.getColor(), unitStrFrom);
                AddUnitService.addUnits(event, tile, game, player.getColor(), unitStrTo);
            }
            if (!player.hasUnit("ralnel_flagship")) movedFlagship = false;
            game.removeStoredValue("OjzRetreatMap");

            String msg = player.getRepresentation() + " Retreated the following units to "
                    + tile.getRepresentationForButtons(game, player);
            msg += " using Watchful Ojz, the Ral Nel commander:";
            msg += TeHelperAbilities.unitSummary(game, player, ojzMap);

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            ButtonHelper.deleteMessage(event);
            if (movedFlagship) {
                TeHelperUnits.serveLastDispatchButtons(game, player, matcher.group("pos"));
            }
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Error " + Constants.jazzPing());
        }
    }

    public static List<Button> getWatchfulOjzUnitButtons(
            Game game, Player player, Tile source, List<String> movedUnits) {
        // Get buttons to move units from this system
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder uh : source.getUnitHolders().values()) {
            String uhName = "space".equals(uh.getName()) ? "Space" : Helper.getPlanetRepresentation(uh.getName(), game);
            for (UnitKey uk : uh.getUnitsByState().keySet()) {
                // franken compat
                if (List.of(UnitType.Pds, UnitType.Spacedock).contains(uk.getUnitType())
                        && !player.hasAbility("miniaturization")) continue;
                if (Objects.equals(UnitType.PlenaryOrbital, uk.getUnitType())) continue;

                // moved all of this unit already from this unit holder
                String unitStr = uk.asyncID() + " " + uh.getName();
                if (movedUnits != null
                        && movedUnits.stream().filter(s -> s.equals(unitStr)).count()
                                >= uh.getUnits().get(uk)) continue;

                // otherwise, add the button
                String id = player.finChecker() + "moveOjzRetreatS1_" + source.getPosition() + "_" + uk.asyncID() + "_"
                        + uh.getName();
                String label = uk.getUnitType().humanReadableName() + " from " + uhName;
                buttons.add(Buttons.green(id, label, uk.unitEmoji()));
            }
        }

        // Get buttons to UNDO moving units from this system
        if (movedUnits != null) {
            Set<String> uniqueUnits = new HashSet<>(movedUnits);
            for (String unit : uniqueUnits) {
                String[] data = unit.split(" ");
                UnitType type = Units.findUnitType(data[0]);
                String uhName = "space".equals(data[1]) ? "Space" : Helper.getPlanetRepresentation(data[1], game);
                if (type != null) {
                    String id = player.finChecker() + "undoOjzRetreatS1_" + source.getPosition() + "_" + type
                            + "_" + data[1];
                    String label = "Return " + type.humanReadableName() + " to " + uhName;
                    buttons.add(Buttons.red(id, label, type.getUnitTypeEmoji()));
                }
            }
        }

        // Choose another system button
        buttons.add(Buttons.gray(player.finChecker() + "ojzRetreatS2_" + source.getPosition(), "Pick a destination"));
        return buttons;
    }
}
