package ti4.discord.interactions.buttons.handlers.unit;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.handlers.actioncards.theodisi.MirrorShieldingLLButtonHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.ponthous.*;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.vanguard.VanguardTechHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.vanguard.VanguardUnitHandler;
import ti4.discord.interactions.buttons.ids.UnitPickButtonIds;
import ti4.discord.interactions.buttons.ids.UnitPickButtonIds.ParsedBulk;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.service.leader.CommanderUnlockCheckService;
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

        Optional<UnitPickButtonIds.Parsed> picked = parseOnMap(game, UnitPickButtonIds.ASSIGN_HITS, buttonID);
        if (picked.isPresent()) {
            assignHitsToPickedUnit(event, game, player, picked.get(), assignHitsType);
            return;
        }

        Optional<ParsedBulk> bulk = UnitPickButtonIds.tryParseBulk(UnitPickButtonIds.ASSIGN_HITS, buttonID)
                .filter(command -> game.getTileByPosition(command.position()) != null);
        if (bulk.isPresent()) {
            assignHitsToAllUnits(event, game, player, bulk.get(), assignHitsType);
            return;
        }

        // Refresh buttons if there was an error
        String pos = buttonID.split("_")[1];
        Tile system = game.getTileByPosition(pos);
        List<Button> systemButtons =
                ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, system, assignHitsType);
        MessageHelper.editMessageButtons(event, systemButtons);
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Encountered error. The buttons have been refreshed, please try again.");
    }

    private static void assignHitsToPickedUnit(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            UnitPickButtonIds.Parsed picked,
            String assignHitsType) {
        boolean combat = assignHitsType.contains("combat");
        boolean remove = "remove".equals(assignHitsType);
        Tile tile = game.getTileByPosition(picked.position());
        int amt = picked.amount();
        UnitState state = picked.state();
        UnitType type = picked.unitType();
        UnitHolder holder = UnitPickerHandlerHelper.pickedUnitHolder(tile, picked);
        ParsedUnit unit = UnitPickerHandlerHelper.ownParsedUnit(player, picked);
        if (remove) {
            RemoveUnitService.removeUnit(event, tile, game, unit, state);
            if (unit.unitKey().unitType() == UnitType.Infantry) {
                ButtonHelper.resolveInfantryRemoval(player, amt, tile);
            }
        } else {
            if (PonthousAbilityHandler.offerLastStand(
                    event, game, player, tile, holder, unit.unitKey(), state, assignHitsType)) {
                return;
            }
            DestroyUnitService.destroyUnit(event, tile, game, unit, combat, state);
            if ("spacecombat".equals(assignHitsType)) {
                VanguardUnitHandler.recordSpaceCombatHitAssignment(game, player, tile, amt);
            }
            IronLeadersHandler.checkCommanderUnlockAfterCombat(game, tile, holder, assignHitsType);
        }

        String verb = remove ? " removed " : " destroyed ";
        String plural = (amt == 1 || "infantry".equalsIgnoreCase(type.humanReadableName())) ? "" : "s";
        String msg = player.getRepresentationNoPing() + verb + amt + " "
                + (picked.hasState() ? state.humanDescr() + " " : "")
                + type.humanReadableName().toLowerCase() + plural;
        msg += UnitPickerHandlerHelper.pickedLocation(game, player, tile, holder, picked) + ".";

        List<Button> systemButtons =
                ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, assignHitsType);
        MessageHelper.editMessageButtons(event, systemButtons);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        FOWCombatThreadMirroring.mirrorMessage(event, game, msg);
    }

    private static void assignHitsToAllUnits(
            ButtonInteractionEvent event, Game game, Player player, ParsedBulk bulk, String assignHitsType) {
        boolean combat = assignHitsType.contains("combat");
        Tile tile = game.getTileByPosition(bulk.position());
        String msg = player.getRepresentationNoPing() + " destroyed all of their units in ";
        switch (bulk.command()) {
            case ALL -> {
                DestroyUnitService.destroyAllPlayerUnitsInSystem(event, game, player, tile, combat);
                IronLeadersHandler.checkCommanderUnlockAfterCombat(game, tile, null, assignHitsType);
                msg += tile.getRepresentationForButtons(game, player);
            }
            case ALL_SHIPS -> {
                UnitHolder space = tile.getSpaceUnitHolder();
                for (UnitKey key : space.getUnitKeys()) {
                    if (!player.unitBelongsToPlayer(key)) continue;
                    UnitModel model = player.getUnitFromUnitKey(key);
                    ParsedUnit unit =
                            ParseUnitService.simpleParsedUnit(player, key.unitType(), space, space.getUnitCount(key));
                    if (model.getIsShip()) {
                        DestroyUnitService.destroyUnit(event, tile, game, unit, combat);
                    } else {
                        RemoveUnitService.removeUnit(event, tile, game, unit);
                    }
                }
                IronLeadersHandler.checkCommanderUnlockAfterCombat(game, tile, space, assignHitsType);
                msg += "the space area of " + tile.getRepresentationForButtons(game, player);
                msg +=
                        ".\n-# Ground forces that were in space were removed, instead of destroyed. If this is not correct, please resolve it manually.";
            }
            default -> {}
        }
        List<Button> systemButtons =
                ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, assignHitsType);
        MessageHelper.editMessageButtons(event, systemButtons);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        FOWCombatThreadMirroring.mirrorMessage(event, game, msg);
    }

    @ButtonHandler("repairDamage_")
    public static void repairDamage(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        Optional<UnitPickButtonIds.Parsed> picked = parseOnMap(game, UnitPickButtonIds.REPAIR_DAMAGE, buttonID);
        if (picked.isEmpty()) {
            Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
            List<Button> repairButtons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, activeSystem);
            MessageHelper.editMessageButtons(event, repairButtons);
            BotLogger.error(new LogOrigin(event, game), "Error matching regex for sustaining hits: " + buttonID);
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Encountered error. The buttons have been refreshed, please try again.");
            return;
        }
        repairPickedUnit(event, game, player, picked.get());
    }

    private static void repairPickedUnit(
            ButtonInteractionEvent event, Game game, Player player, UnitPickButtonIds.Parsed picked) {
        Tile tile = game.getTileByPosition(picked.position());
        int amt = picked.amount();
        UnitType type = picked.unitType();
        UnitHolder holder = UnitPickerHandlerHelper.pickedUnitHolder(tile, picked);
        UnitKey key = Units.getUnitKey(type, player.getColorID());
        if (holder != null) holder.removeDamagedUnit(key, amt);

        String msg = player.getRepresentationNoPing() + " repaired " + amt
                + (picked.hasState() ? " " + picked.state().humanDescr() : "") + type.humanReadableName();
        msg += UnitPickerHandlerHelper.pickedLocation(game, player, tile, holder, picked) + ".";

        List<Button> repairButtons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, tile);
        MessageHelper.editMessageButtons(event, repairButtons);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        FOWCombatThreadMirroring.mirrorMessage(event, game, msg);
    }

    @ButtonHandler("assignDamage_")
    public static void assignDamage(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        Optional<UnitPickButtonIds.Parsed> picked = parseOnMap(game, UnitPickButtonIds.ASSIGN_DAMAGE, buttonID);
        if (picked.isEmpty()) {
            String assignHitsType = getAssignHitsType(game, player);
            Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
            List<Button> systemButtons =
                    ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, activeSystem, assignHitsType);
            MessageHelper.editMessageButtons(event, systemButtons);
            BotLogger.error(new LogOrigin(event, game), "Error matching regex for sustaining hits: " + buttonID);
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Encountered error. The buttons have been refreshed, please try again.");
            return;
        }
        sustainPickedUnit(event, game, player, picked.get());
    }

    private static void sustainPickedUnit(
            ButtonInteractionEvent event, Game game, Player player, UnitPickButtonIds.Parsed picked) {
        Tile tile = game.getTileByPosition(picked.position());
        int amt = picked.amount();
        UnitType type = picked.unitType();
        UnitHolder holder = UnitPickerHandlerHelper.pickedUnitHolder(tile, picked);
        if (holder != null) holder.addDamagedUnit(Units.getUnitKey(type, player.getColorID()), amt);
        UnitModel sustainedUnit = player.getUnitFromUnitKey(Units.getUnitKey(type, player.getColorID()));
        boolean flagbearerSustained = holder == tile.getSpaceUnitHolder()
                && sustainedUnit != null
                && "vanguard_flagship".equals(sustainedUnit.getId());
        VanguardTechHandler.resolveEnhancedPlating(event, game, player, tile, holder, type);
        if (holder == tile.getSpaceUnitHolder() && type == UnitType.Fighter) {
            PonthousUnitHandler.consumeTemporaryFighterSustain(game, player, tile, amt);
        }
        CommanderUnlockCheckService.checkPlayer(player, "ponthous");

        String plural = (amt == 1 || "infantry".equalsIgnoreCase(type.humanReadableName())) ? "" : "s";
        String msg = player.getRepresentationNoPing() + " sustained " + amt + " "
                + (picked.hasState() ? picked.state().humanDescr() : "")
                + type.humanReadableName().toLowerCase() + plural;
        msg += UnitPickerHandlerHelper.pickedLocation(game, player, tile, holder, picked) + ".";
        boolean cancelsTwoHits =
                player.hasTech("nes") || (player.ownsUnit("kryxos_flagship3") && type == UnitType.Flagship);
        if ("spacecombat".equals(getAssignHitsType(game, player))) {
            VanguardUnitHandler.recordSpaceCombatHitAssignment(game, player, tile, cancelsTwoHits ? amt * 2 : amt);
        }
        if (cancelsTwoHits) {
            String sustainSource = player.hasTech("nes")
                    ? "_Non-Euclidean Shielding_"
                    : "the Ultimate Evolution III (the Kryxos flagship)";
            msg += "\n> These SUSTAIN DAMAGE uses cancel 2 hits due to " + sustainSource + ".";
        }
        String assignHitsType = getAssignHitsType(game, player);
        if ("spacecombat".equals(assignHitsType) || "groundcombat".equals(assignHitsType)) {
            MirrorShieldingLLButtonHandler.recordCancelledHits(game, player, tile, cancelsTwoHits ? amt * 2 : amt);
        }
        if (assignHitsType.contains("combat")) {
            AshenUnitHandler.offerAshfallEngineOnSustain(event, game, player, tile, holder, type);
        }
        if (flagbearerSustained && VanguardUnitHandler.offerFlagbearerAfterManualSustain(event, game, player, tile)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            FOWCombatThreadMirroring.mirrorMessage(event, game, msg);
            for (int x = 0; x < amt; x++) {
                ButtonHelperCommanders.resolveLetnevCommanderCheck(player, game, event);
            }
            return;
        }
        List<Button> systemButtons =
                ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, assignHitsType);
        MessageHelper.editMessageButtons(event, systemButtons);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        FOWCombatThreadMirroring.mirrorMessage(event, game, msg);

        for (int x = 0; x < amt; x++) {
            ButtonHelperCommanders.resolveLetnevCommanderCheck(player, game, event);
        }
    }

    private static Optional<UnitPickButtonIds.Parsed> parseOnMap(Game game, String action, String buttonID) {
        return UnitPickButtonIds.tryParse(action, buttonID)
                .filter(picked -> game.getTileByPosition(picked.position()) != null);
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
