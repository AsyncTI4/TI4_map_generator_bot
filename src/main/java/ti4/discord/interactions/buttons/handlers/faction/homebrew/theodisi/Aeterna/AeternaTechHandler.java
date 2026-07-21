package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Aeterna;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class AeternaTechHandler {
    private static final String LATTICE = "thaeternar";
    private static final String USE_LATTICE = "useThanatocyteLattice_";
    private static final String SELECT_LATTICE_SHIP = "selectThanatocyteLatticeShip_";
    private static final String RESOLVE_LATTICE_HIT = "resolveThanatocyteLatticeHit_";

    public static void resetThanatocyteLatticeForCombat(Game game, Player player, Tile tile, String unitHolderName) {
        if (game == null || player == null || tile == null || unitHolderName == null) {
            return;
        }

        String keyPrefix = player.getFaction() + "thanatocyteLattice" + tile.getPosition() + unitHolderName;
        new ArrayList<>(game.getStoredValueMap().keySet())
                .stream().filter(key -> key.startsWith(keyPrefix)).forEach(game::removeStoredValue);
    }

    public static void offerThanatocyteLattice(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits) {
        if (event == null || game == null || destroyedUnits == null || destroyedUnits.isEmpty()) {
            return;
        }

        var combat = StartCombatService.getCurrentCombat(game);
        if (combat == null || combat.tilePosition() == null || combat.unitHolderName() == null) {
            return;
        }

        Tile tile = game.getTileByPosition(combat.tilePosition());
        UnitHolder combatHolder = tile == null ? null : tile.getUnitHolders().get(combat.unitHolderName());
        if (combatHolder == null) {
            return;
        }

        Set<UnitType> destroyedShipTypes = new LinkedHashSet<>();
        for (RemovedUnit destroyedUnit : destroyedUnits) {
            if (destroyedUnit.tile() != tile || destroyedUnit.uh() != combatHolder) {
                continue;
            }

            Player destroyedUnitOwner = destroyedUnit.getPlayer(game);
            UnitModel destroyedUnitModel =
                    destroyedUnitOwner == null ? null : destroyedUnitOwner.getUnitFromUnitKey(destroyedUnit.unitKey());

            if (destroyedUnit.getTotalRemoved() > 0 && destroyedUnitModel != null && destroyedUnitModel.getIsShip()) {
                destroyedShipTypes.add(destroyedUnit.unitKey().unitType());
            }
        }

        if (destroyedShipTypes.isEmpty()) {
            return;
        }

        for (String faction : combat.factions()) {
            Player player = game.getPlayerFromColorOrFaction(faction);
            if (player == null || !player.hasTech(LATTICE)) {
                continue;
            }

            String usageKey = player.getFaction()
                    + "thanatocyteLattice"
                    + tile.getPosition()
                    + combatHolder.getName()
                    + combat.round();

            if (!game.getStoredValue(usageKey).isBlank()) {
                continue;
            }

            boolean hasEligibleOpponentShip = combatHolder.getUnitKeys().stream()
                    .anyMatch(unitKey -> {
                        Player unitOwner = game.getPlayerFromColorOrFaction(unitKey.colorID());
                        UnitModel unitModel = unitOwner == null ? null : unitOwner.getUnitFromUnitKey(unitKey);

                        return unitOwner != null
                                && unitOwner != player
                                && combat.factions().contains(unitOwner.getFaction())
                                && unitModel != null
                                && unitModel.getIsShip()
                                && destroyedShipTypes.contains(unitKey.unitType());
                    });

            if (!hasEligibleOpponentShip) {
                continue;
            }

            long destroyedTypeMask = 0;
            for (UnitType type : destroyedShipTypes) {
                destroyedTypeMask |= 1L << type.ordinal();
            }

            String buttonID = player.factionButtonChecker()
                    + USE_LATTICE
                    + tile.getPosition()
                    + "_"
                    + combatHolder.getName()
                    + "_"
                    + Long.toString(destroyedTypeMask, 36)
                    + "_"
                    + combat.round();

            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + ", opposing ships matching a ship destroyed this combat may be assigned 1 hit via "
                            + "_Thanatocyte Lattice_.",
                    List.of(
                            Buttons.red(buttonID, "Use Thanatocyte Lattice"),
                            Buttons.gray("deleteButtons", "Decline")));
        }
    }

    @ButtonHandler(USE_LATTICE)
    public static void selectThanatocyteLatticeTarget(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String[] parts = buttonID.substring(USE_LATTICE.length()).split("_", 4);
        if (parts.length != 4 || !player.hasTech(LATTICE)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        UnitHolder combatHolder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        if (combatHolder == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        long destroyedTypeMask;
        int combatRound;
        try {
            destroyedTypeMask = Long.parseLong(parts[2], 36);
            combatRound = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String usageKey =
                player.getFaction() + "thanatocyteLattice" + tile.getPosition() + combatHolder.getName() + combatRound;

        if (!game.getStoredValue(usageKey).isBlank()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        Set<String> addedTargets = new LinkedHashSet<>();

        for (var unitKey : combatHolder.getUnitKeys()) {
            Player targetPlayer = game.getPlayerFromColorOrFaction(unitKey.colorID());
            UnitModel unitModel = targetPlayer == null ? null : targetPlayer.getUnitFromUnitKey(unitKey);

            boolean destroyedTypeMatches =
                    (destroyedTypeMask & (1L << unitKey.unitType().ordinal())) != 0;
            if (targetPlayer == null
                    || targetPlayer == player
                    || unitModel == null
                    || !unitModel.getIsShip()
                    || !destroyedTypeMatches
                    || !addedTargets.add(targetPlayer.getColorID() + unitKey.unitType().value)) {
                continue;
            }

            buttons.add(Buttons.red(
                    player.factionButtonChecker()
                            + SELECT_LATTICE_SHIP
                            + tile.getPosition()
                            + "_"
                            + combatHolder.getName()
                            + "_"
                            + targetPlayer.getColorID()
                            + "_"
                            + unitKey.unitType().value
                            + "_"
                            + combatRound,
                    "Assign hit to "
                            + targetPlayer.getFactionNameOrColor()
                            + " "
                            + unitKey.unitType().humanReadableName()));
        }

        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        buttons.add(Buttons.gray("deleteButtons", "Decline"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose the opposing ship type to assign the hit to:",
                buttons);
    }

    @ButtonHandler(SELECT_LATTICE_SHIP)
    public static void offerThanatocyteLatticeHitResolution(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String[] parts = buttonID.substring(SELECT_LATTICE_SHIP.length()).split("_", 5);
        if (parts.length != 5 || !player.hasTech(LATTICE)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        UnitHolder combatHolder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        Player targetPlayer = game.getPlayerFromColorOrFaction(parts[2]);
        UnitType unitType = Units.findUnitType(parts[3]);

        int combatRound;
        try {
            combatRound = Integer.parseInt(parts[4]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (combatHolder == null || targetPlayer == null || targetPlayer == player || unitType == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitKey targetUnitKey = combatHolder.getUnitKeys().stream()
                .filter(unitKey -> targetPlayer.unitBelongsToPlayer(unitKey))
                .filter(unitKey -> unitKey.unitType() == unitType)
                .findFirst()
                .orElse(null);

        UnitModel targetUnitModel = targetUnitKey == null ? null : targetPlayer.getUnitFromUnitKey(targetUnitKey);

        if (targetUnitModel == null || !targetUnitModel.getIsShip()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String usageKey =
                player.getFaction() + "thanatocyteLattice" + tile.getPosition() + combatHolder.getName() + combatRound;

        if (!game.getStoredValue(usageKey).isBlank()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(usageKey, "used");
        ButtonHelper.deleteMessage(event);

        List<Button> buttons = new ArrayList<>();
        for (UnitState state : UnitState.values()) {
            if (combatHolder.getUnitCountForState(targetUnitKey, state) < 1) {
                continue;
            }

            String idBase = targetPlayer.factionButtonChecker()
                    + RESOLVE_LATTICE_HIT
                    + tile.getPosition()
                    + "_"
                    + combatHolder.getName()
                    + "_"
                    + targetPlayer.getColorID()
                    + "_"
                    + unitType.value
                    + "_"
                    + state.name()
                    + "_"
                    + player.getColorID()
                    + "_"
                    + combatRound;

            if (!state.isDamaged() && ButtonHelper.unitCanSustainDamage(game, targetPlayer, tile, targetUnitModel)) {
                buttons.add(Buttons.gray(idBase + "_sustain", "Sustain " + unitType.humanReadableName()));
            }

            buttons.add(Buttons.red(idBase + "_destroy", "Destroy " + unitType.humanReadableName()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                game.isFowMode() ? targetPlayer.getCorrectChannel() : event.getMessageChannel(),
                targetPlayer.getRepresentation()
                        + ", resolve the  _Thanatocyte Lattice_ hit assigned to your "
                        + unitType.humanReadableName()
                        + ":",
                buttons);
    }

    @ButtonHandler(RESOLVE_LATTICE_HIT)
    public static void resolveThanatocyteLatticeHit(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String[] parts = buttonID.substring(RESOLVE_LATTICE_HIT.length()).split("_", 8);
        if (parts.length != 8) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        UnitHolder combatHolder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        Player targetPlayer = game.getPlayerFromColorOrFaction(parts[2]);
        UnitType unitType = Units.findUnitType(parts[3]);
        UnitState state = Units.findUnitState(parts[4]);
        Player sourcePlayer = game.getPlayerFromColorOrFaction(parts[5]);

        int combatRound;
        try {
            combatRound = Integer.parseInt(parts[6]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        boolean sustain = "sustain".equals(parts[7]);
        if (combatHolder == null
                || targetPlayer != player
                || sourcePlayer == null
                || !sourcePlayer.hasTech(LATTICE)
                || unitType == null
                || state == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        var combat = StartCombatService.getCurrentCombat(game);
        if (combat == null
                || !tile.getPosition().equals(combat.tilePosition())
                || !combatHolder.getName().equals(combat.unitHolderName())
                || combat.round() != combatRound
                || !combat.factions().contains(sourcePlayer.getFaction())
                || !combat.factions().contains(targetPlayer.getFaction())) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String usageKey = sourcePlayer.getFaction()
                + "thanatocyteLattice"
                + tile.getPosition()
                + combatHolder.getName()
                + combatRound;

        if (!"used".equals(game.getStoredValue(usageKey))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitKey targetUnitKey = combatHolder.getUnitKeys().stream()
                .filter(unitKey -> targetPlayer.unitBelongsToPlayer(unitKey))
                .filter(unitKey -> unitKey.unitType() == unitType)
                .findFirst()
                .orElse(null);

        UnitModel targetUnitModel = targetUnitKey == null ? null : targetPlayer.getUnitFromUnitKey(targetUnitKey);

        if (targetUnitModel == null || combatHolder.getUnitCountForState(targetUnitKey, state) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (sustain) {
            if (state.isDamaged() || !ButtonHelper.unitCanSustainDamage(game, targetPlayer, tile, targetUnitModel)) {
                ButtonHelper.deleteMessage(event);
                return;
            }

            combatHolder.addDamagedUnit(targetUnitKey, 1);
            CommanderUnlockCheckService.checkPlayer(targetPlayer, "ponthous");
        } else {
            DestroyUnitService.destroyUnit(
                    event, tile, game, new ParsedUnit(targetUnitKey, 1, combatHolder.getName()), true, state);
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                targetPlayer.getRepresentationNoPing()
                        + (sustain ? " sustained" : " destroyed")
                        + " 1 "
                        + unitType.plainName()
                        + " due to _Thanatocyte Lattice_.");
    }
}
