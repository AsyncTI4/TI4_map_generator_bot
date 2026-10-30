package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.iron;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class IronUnitHandler {
    private static final String DOMINION_USED = "ironDominionUsed_";

    public static void offerTideyardDeploy(Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "deployTideyard_" + tile.getPosition(),
                "Deploy Tideyard",
                FactionEmojis.iron));
        buttons.add(Buttons.DECLINE_DELETE_BUTTONS);

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may remove 2 mechs from this system to DEPLOY a Tideyard to a planet in this system.",
                buttons);
    }

    @ButtonHandler("deployTideyard_")
    public static void getTideyardMechButtons(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tilePos = buttonID.replace("deployTideyard_", "");
        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> mechButtons = new ArrayList<>();

        for (UnitHolder holder : tile.getUnitHolders().values()) {
            int mechCount = holder.getUnitCount(UnitType.Mech, player);

            for (int mech = 1; mech <= mechCount; mech++) {
                mechButtons.add(Buttons.gray(
                        player.factionButtonChecker()
                                + "ironRemoveMech_"
                                + tile.getPosition()
                                + "|"
                                + holder.getName()
                                + "|"
                                + mech,
                        "Remove Mech from " + holder.getName(),
                        UnitEmojis.mech));
            }
        }
        if (mechButtons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        mechButtons.add(
                Buttons.red(player.factionButtonChecker() + "finishDeployTideyard_" + tile.getPosition(), "Done"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please select which 2 mechs to remove to DEPLOY Tideyard.",
                mechButtons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ironRemoveMech_")
    public static void removeMech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring("ironRemoveMech_".length()).split("\\|", 3);
        if (payload.length != 3) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        Tile tile = game.getTileByPosition(payload[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(payload[1]);

        if (holder == null || holder.getUnitCount(UnitType.Mech, player) < 1) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        RemoveUnitService.removeUnit(event, tile, game, player, holder, UnitType.Mech, 1);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("finishDeployTideyard_")
    public static void finishTideyardDeploy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tilePos = buttonID.replace("finishDeployTideyard_", "");
        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> placementButtons =
                Helper.getPlanetPlaceUnitButtons(player, game, "sd", "placeOneNDone_skipbuild").stream()
                        .filter(button -> tile.getPlanetUnitHolders().stream()
                                .anyMatch(planet -> button.getCustomId().endsWith("_" + planet.getName())))
                        .toList();

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please select which planet to deploy _Tideyard_ to.",
                placementButtons);

        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getDominionButton(Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + "repairWithDominion_" + tile.getPosition(),
                "Repair 1 Unit",
                FactionEmojis.iron));
        buttons.add(Buttons.DECLINE_DELETE_BUTTONS);

        return buttons;
    }

    public static boolean canOfferDominionRepair(
            Game game, Player player, Tile tile, List<RemovedUnit> destroyedUnits) {
        return hasIronDominion(game, player, tile)
                && hasRepairableUnit(player, tile)
                && !hasUsedDominionThisRound(game, player)
                && destroyedUnits.stream().anyMatch(unit -> isOtherNonFighterShip(player, unit));
    }

    public static void markDominionUsed(Game game, Player player) {
        game.setStoredValue(getDominionStateKey(game, player), "true");
    }

    public static void clearDominionState(Game game) {
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(DOMINION_USED))
                .toList()
                .forEach(game::removeStoredValue);
    }

    @ButtonHandler("repairWithDominion_")
    public static void sendRepairButtonsForDominion(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tilePos = buttonID.replace("repairWithDominion_", "");
        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null || !hasIronDominion(game, player, tile) || !hasRepairableUnit(player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        markDominionUsed(game, player);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please select the unit you would like to repair using _Iron Dominion_.",
                ButtonHelper.getButtonsForRepairingUnitsInASystem(player, tile));

        ButtonHelper.deleteMessage(event);
    }

    private static boolean isOtherNonFighterShip(Player player, RemovedUnit unit) {
        UnitModel unitModel = player.getUnitFromUnitKey(unit.unitKey());
        return player.unitBelongsToPlayer(unit.unitKey())
                && unit.unitKey().unitType() != UnitType.Flagship
                && unitModel != null
                && unitModel.isNonFighterShip();
    }

    private static boolean hasIronDominion(Game game, Player player, Tile tile) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        return player.hasUnit("iron_flagship")
                && ButtonHelper.doesPlayerHaveFSHere("iron_flagship", player, tile)
                && combat != null
                && tile.getPosition().equals(combat.tilePosition());
    }

    private static boolean hasRepairableUnit(Player player, Tile tile) {
        return ButtonHelper.getButtonsForRepairingUnitsInASystem(player, tile).size() > 1;
    }

    private static boolean hasUsedDominionThisRound(Game game, Player player) {
        return "true".equals(game.getStoredValue(getDominionStateKey(game, player)));
    }

    private static String getDominionStateKey(Game game, Player player) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null || combat.tilePosition() == null) return DOMINION_USED + player.getFaction();
        return DOMINION_USED + player.getFaction() + "_" + combat.tilePosition() + "_" + combat.unitHolderName() + "_"
                + combat.round();
    }
}
