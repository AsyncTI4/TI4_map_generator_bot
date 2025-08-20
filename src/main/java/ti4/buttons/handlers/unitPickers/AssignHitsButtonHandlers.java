package ti4.buttons.handlers.unitPickers;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.model.UnitModel;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.service.regex.RegexService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParseUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
class AssignHitsButtonHandlers {

    @ButtonHandler("assignHits_")
    // assignHits_101_2_dd
    public static void assignHits(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String assignHitsType = getAssignHitsType(game, player);
        boolean combat = assignHitsType.contains("combat");
        boolean remove = "remove".equals(assignHitsType);

        // Assign hits to single unit
        String regexSingleUnit = UnitPickerHandlerHelper.singleUnitRegex(game, "assignHits");
        boolean success = RegexService.runMatcher(
                regexSingleUnit,
                buttonID,
                matcher -> {
                    Tile tile = game.getTileByPosition(matcher.group("pos"));
                    int amt = Integer.parseInt(matcher.group("amt"));
                    boolean prefersState =
                            matcher.group("state") != null && StringUtils.isNotBlank(matcher.group("state"));
                    UnitState state = prefersState ? Units.findUnitState(matcher.group("state")) : UnitState.none;
                    UnitType type = Units.findUnitType(matcher.group("unittype"));
                    String planetName = matcher.group("planet");
                    UnitHolder holder =
                            planetName != null ? tile.getUnitHolderFromPlanet(planetName) : tile.getSpaceUnitHolder();
                    ParsedUnit unit = UnitPickerHandlerHelper.parsedUnitFromMatcher(player, matcher);
                    if (remove) {
                        RemoveUnitService.removeUnit(event, tile, game, unit, state);
                    } else {
                        DestroyUnitService.destroyUnit(event, tile, game, unit, combat, state);
                    }

                    String verb = remove ? " removed " : " destroyed ";
                    String msg = player.getRepresentationNoPing() + verb + amt + " "
                            + (prefersState ? state.humanDescr() + " " : "")
                            + type.humanReadableName().toLowerCase();
                    msg += (planetName != null && holder != null
                                    ? " on " + holder.getRepresentation(game)
                                    : " in tile " + tile.getRepresentationForButtons(game, player))
                            + ".";

                    List<Button> systemButtons =
                            ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, assignHitsType);
                    MessageHelper.editMessageButtons(event, systemButtons);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                    FOWCombatThreadMirroring.mirrorMessage(event, game, msg);
                },
                x -> {});
        if (success) return;

        // Assign hits to "all units"
        String regexAllCmd = "assignHits";
        regexAllCmd += "_" + RegexHelper.posRegex(game);
        regexAllCmd += "_" + "(?<cmd>(All|AllShips))";
        success = RegexService.runMatcher(
                regexAllCmd,
                buttonID,
                matcher -> {
                    Tile tile = game.getTileByPosition(matcher.group("pos"));
                    String msg = player.getRepresentationNoPing() + " destroyed all of their units in ";
                    switch (matcher.group("cmd")) {
                        case "All" -> {
                            DestroyUnitService.destroyAllPlayerUnitsInSystem(event, game, player, tile, combat);
                            msg += tile.getRepresentationForButtons(game, player);
                        }
                        case "AllShips" -> {
                            UnitHolder space = tile.getSpaceUnitHolder();
                            for (UnitKey key : space.getUnitKeys()) {
                                if (!player.unitBelongsToPlayer(key)) continue;
                                UnitModel model = player.getUnitFromUnitKey(key);
                                ParsedUnit unit = ParseUnitService.simpleParsedUnit(
                                        player, key.getUnitType(), space, space.getUnitCount(key));
                                if (model.getIsShip()) {
                                    DestroyUnitService.destroyUnit(event, tile, game, unit, combat);
                                } else {
                                    RemoveUnitService.removeUnit(event, tile, game, unit);
                                }
                            }
                            msg += "the space area of " + tile.getRepresentationForButtons(game, player);
                            msg +=
                                    ".\n-# Ground forces that were in space were removed, instead of destroyed. If this is not correct, please resolve it manually.";
                        }
                    }
                    List<Button> systemButtons =
                            ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, assignHitsType);
                    MessageHelper.editMessageButtons(event, systemButtons);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                    FOWCombatThreadMirroring.mirrorMessage(event, game, msg);
                },
                x -> {});
        if (success) return;

        // Refresh buttons if there was an error
        String pos = buttonID.split("_")[1];
        Tile system = game.getTileByPosition(pos);
        List<Button> systemButtons =
                ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, system, assignHitsType);
        MessageHelper.editMessageButtons(event, systemButtons);
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Encountered error. The buttons have been refreshed, please try again.");
    }

    @ButtonHandler("repairDamage_")
    public static void repairDamage(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String regex = UnitPickerHandlerHelper.singleUnitRegex(game, "repairDamage");
        RegexService.runMatcher(
                regex,
                buttonID,
                matcher -> {
                    Tile tile = game.getTileByPosition(matcher.group("pos"));
                    int amt = Integer.parseInt(matcher.group("amt"));
                    UnitType type = Units.findUnitType(matcher.group("unittype"));
                    boolean prefersState =
                            matcher.group("state") != null && StringUtils.isNotBlank(matcher.group("state"));
                    UnitState state = prefersState ? Units.findUnitState(matcher.group("state")) : UnitState.none;
                    String planetName = matcher.group("planet");
                    String color = matcher.group("color");
                    UnitHolder holder =
                            planetName != null ? tile.getUnitHolderFromPlanet(planetName) : tile.getSpaceUnitHolder();
                    UnitKey key = Units.getUnitKey(type, player.getColorID());
                    if (holder != null) holder.removeDamagedUnit(key, amt);

                    String msg = player.getRepresentationNoPing() + " repaired " + amt
                            + (prefersState ? " " + state.humanDescr() : "") + type.humanReadableName();
                    msg += (planetName != null && holder != null
                                    ? " on " + holder.getRepresentation(game)
                                    : " in tile " + tile.getRepresentationForButtons(game, player))
                            + ".";

                    List<Button> repairButtons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, game, tile);
                    MessageHelper.editMessageButtons(event, repairButtons);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                    FOWCombatThreadMirroring.mirrorMessage(event, game, msg);
                },
                x -> {
                    // Refresh buttons if there was an error
                    Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
                    List<Button> repairButtons =
                            ButtonHelper.getButtonsForRepairingUnitsInASystem(player, game, activeSystem);
                    MessageHelper.editMessageButtons(event, repairButtons);
                    BotLogger.error(
                            new LogOrigin(event, game), "Error matching regex for sustaining hits: " + buttonID);
                    MessageHelper.sendEphemeralMessageToEventChannel(
                            event, "Encountered error. The buttons have been refreshed, please try again.");
                });
    }

    @ButtonHandler("assignDamage_")
    public static void assignDamage(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String regex = UnitPickerHandlerHelper.singleUnitRegex(game, "assignDamage");
        RegexService.runMatcher(
                regex,
                buttonID,
                matcher -> {
                    Tile tile = game.getTileByPosition(matcher.group("pos"));
                    int amt = Integer.parseInt(matcher.group("amt"));
                    UnitType type = Units.findUnitType(matcher.group("unittype"));
                    boolean prefersState =
                            matcher.group("state") != null && StringUtils.isNotBlank(matcher.group("state"));
                    UnitState state = prefersState ? Units.findUnitState(matcher.group("state")) : UnitState.none;
                    String planetName = matcher.group("planet");
                    String color = matcher.group("color");
                    UnitHolder holder =
                            planetName != null ? tile.getUnitHolderFromPlanet(planetName) : tile.getSpaceUnitHolder();
                    if (holder != null) holder.addDamagedUnit(Units.getUnitKey(type, player.getColorID()), amt);

                    String msg = player.getRepresentationNoPing() + " sustained " + amt + " "
                            + (prefersState ? state.humanDescr() : "") + type.humanReadableName();
                    msg += (planetName != null && holder != null
                                    ? " on " + holder.getRepresentation(game)
                                    : " in tile " + tile.getRepresentationForButtons(game, player))
                            + ".";
                    if (player.hasTech("nes"))
                        msg += "\n> - These sustains cancel 2 hits due to _Non-Euclidean Shielding_.";
                    String assignHitsType = getAssignHitsType(game, player);
                    List<Button> systemButtons =
                            ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, assignHitsType);
                    MessageHelper.editMessageButtons(event, systemButtons);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                    FOWCombatThreadMirroring.mirrorMessage(event, game, msg);

                    for (int x = 0; x < amt; x++) {
                        ButtonHelperCommanders.resolveLetnevCommanderCheck(player, game, event);
                    }
                },
                x -> {
                    // Refresh buttons if there was an error
                    String assignHitsType = getAssignHitsType(game, player);
                    Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
                    List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(
                            player, game, activeSystem, assignHitsType);
                    MessageHelper.editMessageButtons(event, systemButtons);
                    BotLogger.error(
                            new LogOrigin(event, game), "Error matching regex for sustaining hits: " + buttonID);
                    MessageHelper.sendEphemeralMessageToEventChannel(
                            event, "Encountered error. The buttons have been refreshed, please try again.");
                });
    }

    @ButtonHandler("getDamageButtons_")
    public static void getDamageButtons(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (buttonID.contains("deleteThis")) {
            buttonID = buttonID.replace("deleteThis", "");
            ButtonHelper.deleteMessage(event);
        }
        String pos = buttonID.split("_")[1];
        String assignType = "combat";
        if (buttonID.split("_").length > 2) {
            assignType = buttonID.split("_")[2];
        }
        List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(
                player, game, game.getTileByPosition(pos), assignType);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), player.getRepresentationUnfogged() + " Use buttons to resolve", buttons);
    }

    private String getAssignHitsType(Game game, Player player) {
        String key = player.getFaction() + "latestAssignHits";
        if (game.getStoredValue(key).isBlank()) return "combat";
        return game.getStoredValue(key).toLowerCase();
    }
}
