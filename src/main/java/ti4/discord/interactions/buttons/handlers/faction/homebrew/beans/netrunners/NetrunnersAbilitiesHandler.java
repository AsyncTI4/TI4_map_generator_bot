package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperUnits;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.transaction.SendDebtService;

@UtilityClass
public class NetrunnersAbilitiesHandler {

    public static final String SYSTEM_BREACH_ABILITY = "system_breach";
    public static final String CONTROL_NETWORK_ABILITY = "control_network";
    public static final String RANSOMWARE_ABILITY = "ransomware";
    public static final String SYSTEM_BREACH_POOL = "hackerman";
    private static final String CONTROL_NETWORK_PRODUCTION_TILE = "controlNetworkProductionTile";
    private static final String CONTROL_NETWORK_PRODUCTION_LIMIT = "controlNetworkProductionLimit";
    private static final String CONTROL_NETWORK_PRODUCTION_TARGET = "controlNetworkProductionTarget";
    private static final String CONTROL_NETWORK_SPACE_CANNON_TILE = "controlNetworkSpaceCannonTile";
    private static final String CONTROL_NETWORK_SPACE_CANNON_HOLDER = "controlNetworkSpaceCannonHolder";
    private static final String CONTROL_NETWORK_SPACE_CANNON_ROLL = "controlNetworkSpaceCannonRoll";

    public static void resolveSystemBreach(Game game, UnitKey unitKey, int count) {
        if (game == null || unitKey == null || count < 1) {
            return;
        }

        Player placingPlayer = game.getPlayerFromColorOrFaction(unitKey.getColor());
        UnitModel unitModel = placingPlayer == null ? null : placingPlayer.getUnitFromUnitKey(unitKey);
        if (placingPlayer == null || unitModel == null || !unitModel.getIsStructure()) {
            return;
        }

        if (game.getDebtPoolIcon(SYSTEM_BREACH_POOL) == null) {
            game.setDebtPoolIcon(SYSTEM_BREACH_POOL, FactionEmojis.netrunners.toString());
        }

        for (Player netrunner : game.getRealPlayers()) {
            if (netrunner != placingPlayer && netrunner.hasAbility(SYSTEM_BREACH_ABILITY)) {
                SendDebtService.sendDebt(placingPlayer, netrunner, count, SYSTEM_BREACH_POOL);

                MessageHelper.sendMessageToChannel(
                        netrunner.getCorrectChannel(),
                        netrunner.getRepresentation() + " used **System Breach** to place " + count + " of "
                                + placingPlayer.getRepresentation(false, true)
                                + "'s control token" + (count == 1 ? "" : "s") + " in their **" + SYSTEM_BREACH_POOL
                                + "** pool."
                                + " This is technically optional, done automatically for convenience.");
            }
        }
    }

    private static CombatModifierModel getControlNetworkSpaceCannonModifier(CombatRollType rollType) {
        CombatModifierModel modifier = new CombatModifierModel();
        modifier.setAlias("netrunners_control_network");
        modifier.setType("mods");
        modifier.setValue(-1);
        modifier.setPersistenceType(Constants.MOD_TEMP_ONE_ROUND.toString());
        modifier.setScope("");
        modifier.setRelated(List.of());
        modifier.setForCombatAbility(rollType);
        return modifier;
    }

    public static List<Button> getControlNetworkSpaceCannonButtons(
            Game game, Player rollingPlayer, Tile tile, CombatRollType rollType, String unitHolderName) {
        List<Button> buttons = new ArrayList<>();
        for (Player netrunner : game.getRealPlayers()) {
            if (netrunner == rollingPlayer
                    || !netrunner.hasAbility(CONTROL_NETWORK_ABILITY)
                    || netrunner.getDebtTokenCount(rollingPlayer.getColor(), SYSTEM_BREACH_POOL) < 1) {
                continue;
            }
            buttons.add(Buttons.gray(
                    netrunner.factionButtonChecker()
                            + "controlNetworkSpaceCannon_" + rollingPlayer.getColor() + "_"
                            + tile.getPosition() + "_" + rollType + "_" + unitHolderName,
                    "Use Control Network",
                    FactionEmojis.netrunners));
        }
        return buttons;
    }

    @ButtonHandler("controlNetworkSpaceCannon_")
    public static void resolveControlNetworkSpaceCannon(
            Game game, Player netrunner, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("controlNetworkSpaceCannon_", "").split("_", 4);
        if (parts.length < 4) {
            return;
        }

        Player rollingPlayer = game.getPlayerFromColorOrFaction(parts[0]);
        Tile tile = game.getTileByPosition(parts[1]);
        CombatRollType rollType = CombatRollType.valueOf(parts[2]);
        String unitHolderName = parts[3];
        if (rollingPlayer == null
                || tile == null
                || !netrunner.hasAbility(CONTROL_NETWORK_ABILITY)
                || netrunner.getDebtTokenCount(rollingPlayer.getColor(), SYSTEM_BREACH_POOL) < 1) {
            return;
        }

        setControlNetworkSpaceCannonValues(game, rollingPlayer, tile, rollType, unitHolderName);
        netrunner.clearDebt(rollingPlayer, 1, SYSTEM_BREACH_POOL);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                netrunner.getRepresentation() + " used **Control Network** to remove 1 of "
                        + rollingPlayer.getRepresentation(false, true)
                        + "'s control tokens from their **" + SYSTEM_BREACH_POOL
                        + "** pool and apply -1 to their next SPACE CANNON roll.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static List<NamedCombatModifierModel> getPendingControlNetworkSpaceCannonModifier(
            Game game, Player rollingPlayer, Tile tile, UnitHolder unitHolder, CombatRollType rollType) {
        if (tile == null
                || unitHolder == null
                || rollType == null
                || !tile.getTileID()
                        .equals(game.getStoredValue(CONTROL_NETWORK_SPACE_CANNON_TILE + rollingPlayer.getFaction()))
                || !unitHolder
                        .getName()
                        .equals(game.getStoredValue(CONTROL_NETWORK_SPACE_CANNON_HOLDER + rollingPlayer.getFaction()))
                || !rollType.toString()
                        .equals(game.getStoredValue(CONTROL_NETWORK_SPACE_CANNON_ROLL + rollingPlayer.getFaction()))) {
            return List.of();
        }

        cleanupControlNetworkSpaceCannon(game, rollingPlayer);
        return List.of(new NamedCombatModifierModel(
                getControlNetworkSpaceCannonModifier(rollType),
                FactionEmojis.netrunners + " Control Network: -1 to SPACE CANNON rolls"));
    }

    public static void offerRansomwareButtons(Game game) {
        for (Player netrunner : game.getRealPlayers()) {
            if (!netrunner.hasAbility(RANSOMWARE_ABILITY)) {
                continue;
            }
            for (Player payer : game.getRealPlayersExcludingThis(netrunner)) {
                int tokenCount = netrunner.getDebtTokenCount(payer.getColor(), SYSTEM_BREACH_POOL);
                if (payer.getTg() < 1 || tokenCount < 1 || payer.getCardsInfoThread() == null) {
                    continue;
                }

                String message = payer.getRepresentationUnfogged() + ", you may pay 1 trade good to remove 1 of your control tokens from their **"
        + SYSTEM_BREACH_POOL + "** pool via **Ransomware**.";
                List<Button> buttons = List.of(
                        Buttons.green(
                                payer.factionButtonChecker() + "ransomwarePay_" + netrunner.getFaction(),
                                "Pay 1 Trade Good",
                                FactionEmojis.netrunners),
                        Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(payer.getCardsInfoThread(), message, buttons);
            }
        }
    }

    @ButtonHandler("ransomwarePay_")
    public static void resolveRansomware(Game game, Player payer, ButtonInteractionEvent event, String buttonID) {
        Player netrunner = game.getPlayerFromColorOrFaction(buttonID.replace("ransomwarePay_", ""));
        if (netrunner == null
                || !netrunner.hasAbility(RANSOMWARE_ABILITY)
                || payer.getTg() < 1
                || netrunner.getDebtTokenCount(payer.getColor(), SYSTEM_BREACH_POOL) < 1) {
            return;
        }

        payer.gainTG(-1);
        netrunner.gainTG(1, true);
        netrunner.clearDebt(payer, 1, SYSTEM_BREACH_POOL);

        String message = payer.getFactionEmojiOrColor() + " paid 1 trade good to remove 1 of their control tokens from the **"
        + SYSTEM_BREACH_POOL + "** pool via **Ransomware**.";
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(payer.getCorrectChannel(), message);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(netrunner.getCorrectChannel(), message);
        }
    }

    public static Button getControlNetworkCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + "controlNetworkBlockadedDock",
                "Produce at blockaded spacedock (if applicable)",
                FactionEmojis.netrunners);
    }

    @ButtonHandler("controlNetworkBlockadedDock")
    public static void offerControlNetworkBlockadedDockButtons(
            Game game, Player netrunner, ButtonInteractionEvent event) {
        List<Button> buttons = getControlNetworkBlockadedDockButtons(game, netrunner);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    netrunner.getRepresentation()
                            + " has no players with eligible blockaded space docks for **Control Network**.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                netrunner.getRepresentation()
                        + ", please choose the player whose blockaded space dock you wish to use. Note that this can only be done during the production window of a tactical action.",
                buttons);
    }

    @ButtonHandler("controlNetworkProduce_")
    public static void resolveControlNetworkProduction(
            Game game, Player netrunner, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("controlNetworkProduce_", "").split("_");
        if (parts.length < 2) {
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        Tile tile = game.getTileByPosition(parts[1]);
        if (!canUseControlNetworkProduction(game, netrunner, target, tile)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    netrunner.getRepresentation() + " cannot use **Control Network** with that space dock.");
            return;
        }

        int productionValue = getControlNetworkProductionValue(game, target, tile);
        netrunner.clearDebt(target, 1, SYSTEM_BREACH_POOL);
        setControlNetworkProductionValues(game, netrunner, target, tile, productionValue);

        List<Button> buttons = getControlNetworkProductionButtons(event, netrunner, game, tile);
        String productionMessage = netrunner.getRepresentationUnfogged()
                + ", use these buttons to produce ships and fighters. Ground forces cannot be produced with **Control Network**.\n"
                + "You have " + productionValue + " PRODUCTION value in this system.\n"
                + ButtonHelper.getListOfStuffAvailableToSpend(netrunner, game, true);

        String actionMessage = netrunner.getRepresentationUnfogged() + " used **Control Network** to remove 1 of "
                + target.getRepresentation(true, true)
                + "'s control tokens from their **" + SYSTEM_BREACH_POOL
                + "** pool and use the PRODUCTION ability of "
                + target.getRepresentation(true, true)
                + "'s blockaded space dock in "
                + tile.getRepresentationForButtons(game, netrunner)
                + ".";

        MessageHelper.sendMessageToChannel(game.getActionsChannel(), actionMessage);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), productionMessage);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Produce Units", buttons);
    }

    public static String getControlNetworkProductionMessage(
            Game game, Player player, Tile tile, int cost, int unitCount) {
        String keySuffix = player.getFaction();
        if (tile == null
                || !tile.getPosition().equals(game.getStoredValue(CONTROL_NETWORK_PRODUCTION_TILE + keySuffix))) {
            return "";
        }

        String storedProductionLimit = game.getStoredValue(CONTROL_NETWORK_PRODUCTION_LIMIT + keySuffix);
        if (storedProductionLimit.isEmpty()) {
            return "";
        }

        int productionLimit = Integer.parseInt(storedProductionLimit);
        String message = "Producing a total of " + StringHelper.pluralize(unitCount, "unit")
                + " (**Control Network** PRODUCTION limit is " + productionLimit + ")"
                + " for a total cost of " + StringHelper.pluralize(cost, "resource") + ".";
        if (productionLimit < unitCount) {
            message += "\n### Warning! Exceeding **Control Network** PRODUCTION limit of " + productionLimit + "!";
        }
        return message;
    }

    public static void cleanupControlNetworkProduction(Game game, Player player) {
        String keySuffix = player.getFaction();
        game.removeStoredValue(CONTROL_NETWORK_PRODUCTION_TILE + keySuffix);
        game.removeStoredValue(CONTROL_NETWORK_PRODUCTION_LIMIT + keySuffix);
        game.removeStoredValue(CONTROL_NETWORK_PRODUCTION_TARGET + keySuffix);
    }

    private static List<Button> getControlNetworkBlockadedDockButtons(Game game, Player netrunner) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayersExcludingThis(netrunner)) {
            if (netrunner.getDebtTokenCount(target.getColor(), SYSTEM_BREACH_POOL) < 1) {
                continue;
            }
            for (Tile tile : game.getTileMap().values()) {
                if (canUseControlNetworkProduction(game, netrunner, target, tile)) {
                    buttons.add(Buttons.green(
                            netrunner.factionButtonChecker() + "controlNetworkProduce_" + target.getColor() + "_"
                                    + tile.getPosition(),
                            target.getColorDisplayName() + ": " + tile.getRepresentationForButtons(game, netrunner),
                            FactionEmojis.netrunners));
                }
            }
        }
        return buttons;
    }

    private static List<Button> getControlNetworkProductionButtons(
            ButtonInteractionEvent event, Player netrunner, Game game, Tile tile) {
        return Helper.getPlaceUnitButtons(event, netrunner, game, tile, "controlNetwork", "place").stream()
                .filter(button -> !isGroundForceProductionButton(button))
                .toList();
    }

    private static boolean isGroundForceProductionButton(Button button) {
        return NetrunnersUnitsHandler.isGroundForceProductionButton(button);
    }

    private static boolean canUseControlNetworkProduction(Game game, Player netrunner, Player target, Tile tile) {
        return target != null
                && target != netrunner
                && tile != null
                && netrunner.getDebtTokenCount(target.getColor(), SYSTEM_BREACH_POOL) > 0
                && FoWHelper.playerHasActualShipsInSystem(netrunner, tile)
                && tileHasSpaceDockControlledByPlayer(target, tile)
                && getControlNetworkProductionValue(game, target, tile) > 0;
    }

    private static boolean tileHasSpaceDockControlledByPlayer(Player player, Tile tile) {
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder.getUnitCount(UnitType.Spacedock, player.getColor()) > 0) {
                return true;
            }
        }
        return false;
    }

    private static int getControlNetworkProductionValue(Game game, Player target, Tile tile) {
        if (tile.isScar(game) && !target.hasUnlockedBreakthrough("nivynbt")) {
            return 0;
        }
        if (TeHelperUnits.affectedByQuietus(game, target, tile)) {
            return 0;
        }

        int highestProductionValue = 0;
        boolean cosmicSuper = isAdjacentToCosmicSupernova(game, target, tile);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            for (UnitKey unit : unitHolder.getUnits().keySet()) {
                if (!target.unitBelongsToPlayer(unit)) {
                    continue;
                }

                UnitModel unitModel = target.getPriorityUnitByAsyncID(unit.asyncID(), unitHolder);
                if (unitModel == null || !"sd".equals(unitModel.getAsyncId())) {
                    continue;
                }

                int productionValue = unitModel.getProductionValue();
                if ((productionValue == 2
                                || productionValue == 4
                                || target.ownsUnit("mykomentori_spacedock2")
                                || target.ownsUnit("miltymod_spacedock2"))
                        && unitHolder instanceof Planet planet) {
                    if (target.hasUnit("celdauri_spacedock") || target.hasUnit("celdauri_spacedock2")) {
                        productionValue += Math.max(planet.getResources(), planet.getInfluence());
                    } else {
                        productionValue += planet.getResources();
                    }
                    if (target.hasUnit("axis_mech")
                            && !ButtonHelper.isLawInPlay(game, "articles_war")
                            && unitHolder.getUnitCount(UnitType.Mech, target) > 0) {
                        productionValue = Math.max(5, productionValue);
                    }
                }
                if (productionValue > 0 && target.hasRelic("boon_of_the_cerulean_god")) {
                    productionValue++;
                }
                if (productionValue > 0 && cosmicSuper) {
                    productionValue++;
                }
                highestProductionValue = Math.max(highestProductionValue, productionValue);
            }
        }
        return highestProductionValue;
    }

    private static boolean isAdjacentToCosmicSupernova(Game game, Player player, Tile tile) {
        if (!game.isCosmicPhenomenaeMode()) {
            return false;
        }
        for (String pos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true)) {
            Tile adjacentTile = game.getTileByPosition(pos);
            if (adjacentTile != null && adjacentTile.isSupernova()) {
                return true;
            }
        }
        return false;
    }

    private static void setControlNetworkProductionValues(
            Game game, Player netrunner, Player target, Tile tile, int productionValue) {
        String keySuffix = netrunner.getFaction();
        game.setStoredValue(CONTROL_NETWORK_PRODUCTION_TILE + keySuffix, tile.getPosition());
        game.setStoredValue(CONTROL_NETWORK_PRODUCTION_LIMIT + keySuffix, Integer.toString(productionValue));
        game.setStoredValue(CONTROL_NETWORK_PRODUCTION_TARGET + keySuffix, target.getFaction());
    }

    private static void setControlNetworkSpaceCannonValues(
            Game game, Player rollingPlayer, Tile tile, CombatRollType rollType, String unitHolderName) {
        String keySuffix = rollingPlayer.getFaction();
        game.setStoredValue(CONTROL_NETWORK_SPACE_CANNON_TILE + keySuffix, tile.getTileID());
        game.setStoredValue(CONTROL_NETWORK_SPACE_CANNON_HOLDER + keySuffix, unitHolderName);
        game.setStoredValue(CONTROL_NETWORK_SPACE_CANNON_ROLL + keySuffix, rollType.toString());
    }

    private static void cleanupControlNetworkSpaceCannon(Game game, Player rollingPlayer) {
        String keySuffix = rollingPlayer.getFaction();
        game.removeStoredValue(CONTROL_NETWORK_SPACE_CANNON_TILE + keySuffix);
        game.removeStoredValue(CONTROL_NETWORK_SPACE_CANNON_HOLDER + keySuffix);
        game.removeStoredValue(CONTROL_NETWORK_SPACE_CANNON_ROLL + keySuffix);
    }
}
