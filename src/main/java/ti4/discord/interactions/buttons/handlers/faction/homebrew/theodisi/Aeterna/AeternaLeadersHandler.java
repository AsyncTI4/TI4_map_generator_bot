package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Aeterna;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.PlayHeroService;
import ti4.service.leader.UnlockLeaderService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class AeternaLeadersHandler {
    private static final String UNLOCK = "unlockAeternaCommander";
    private static final String PURGE_HERO = "purgeGravecall_";
    private static final String DESTROY_OPPONENT_SHIP = "gravecallDestroyShip_";
    private static final String SELECT_AGENT_SHIP = "selectAeternaAgentShip_";
    private static final String SELECT_AGENT_SYSTEM = "selectAeternaAgentSystem_";

    // Commander Unlock
    public static Button offerAeternaCommanderUnlockButton(Player player) {
        if (player == null || !player.hasLeader("aeternacommander") || player.hasLeaderUnlocked("aeternacommander")) {
            return null;
        }

        return Buttons.green(player.factionButtonChecker() + UNLOCK, "Unlock Vorun Kael", FactionEmojis.aeterna);
    }

    @ButtonHandler(UNLOCK)
    public static void unlockAeternaCommander(ButtonInteractionEvent event, Player player, Game game) {
        if (event == null
                || player == null
                || game == null
                || !player.hasLeader("aeternacommander")
                || player.hasLeaderUnlocked("aeternacommander")) {
            return;
        }

        UnlockLeaderService.unlockLeader("aeternacommander", game, player);
        ButtonHelper.deleteMessage(event);
    }

    // Hero
    public static Button getGravecallButton(Game game, Player player, Tile tile) {
        return Buttons.gray(
                player.factionButtonChecker() + PURGE_HERO + tile.getPosition(),
                "Purge Gravecall",
                FactionEmojis.aeterna);
    }

    @ButtonHandler(PURGE_HERO)
    public static void resolveGravecallStep1(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasLeaderUnlocked("aeternahero")) {
            return;
        }

        String tilePos = buttonID.replace(PURGE_HERO, "");
        Tile activeSystem = game.getTileByPosition(tilePos);
        if (activeSystem == null || activeSystem.isHomeSystem()) {
            return;
        }

        float combinedCost = 0;
        UnitHolder unitHolder = activeSystem.getSpaceUnitHolder();

        for (UnitKey unitKey :
                Set.copyOf(unitHolder.getUnitsByStateForPlayer(player).keySet())) {
            UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
            if (unitModel == null || !unitModel.getIsShip()) {
                continue;
            }

            int count = unitHolder.getUnitCount(unitKey);
            combinedCost += count * unitModel.getCost();
            RemoveUnitService.removeUnit(event, activeSystem, game, player, unitHolder, unitKey.unitType(), count);
        }
        int requiredCost = (int) Math.ceil(combinedCost);

        Leader hero = player.getLeader("aeternahero").orElse(null);
        PlayHeroService.removeLeader(game, player, hero);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " destroyed ships with a total cost of " + requiredCost
                        + " using _Gravecall_.");

        Player opponent = CombatRollService.getOpponent(player, List.of(activeSystem.getSpaceUnitHolder()), game);
        if (opponent == null || requiredCost == 0) return;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                getGravecallDestroyMessage(opponent, requiredCost, 0),
                getGravecallDestroyShipButtons(opponent, activeSystem, requiredCost, 0, 0));

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(DESTROY_OPPONENT_SHIP)
    public static void resolveGravecallDestroyShip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(DESTROY_OPPONENT_SHIP.length()).split("\\|", 5);
        if (values.length < 4) return;

        int requiredCost;
        int fightersDestroyed = 0;
        float destroyedCost;
        try {
            requiredCost = Integer.parseInt(values[1]);
            destroyedCost = Float.parseFloat(values[2]);
            if (values.length == 5) {
                fightersDestroyed = Integer.parseInt(values[3]);
            }
        } catch (NumberFormatException e) {
            return;
        }
        Tile tile = game.getTileByPosition(values[0]);
        if (tile == null || requiredCost < 1 || destroyedCost < 0 || fightersDestroyed < 0) return;

        UnitHolder space = tile.getSpaceUnitHolder();
        String unitAsyncID = values[values.length - 1];
        UnitKey unitKey = space.getUnitKeysForPlayer(player).stream()
                .filter(key -> key.asyncID().equals(unitAsyncID))
                .findFirst()
                .orElse(null);
        UnitModel unitModel = unitKey == null ? null : player.getUnitFromUnitKey(unitKey);
        if (unitModel == null || !unitModel.getIsShip() || space.getUnitCount(unitKey) < 1) return;

        boolean isFighter = unitKey.unitType() == UnitType.Fighter;
        int newFightersDestroyed = fightersDestroyed + (isFighter ? 1 : 0);
        float newDestroyedCost =
                destroyedCost + (isFighter ? (fightersDestroyed % 2 == 0 ? 1 : 0) : unitModel.getCost());
        RemoveUnitService.removeUnit(event, tile, game, player, space, unitKey.unitType(), 1);

        if (newDestroyedCost + 0.001f >= requiredCost) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " destroyed ships with a total cost of "
                            + formatCost(newDestroyedCost) + " for _Gravecall_ (required cost: " + requiredCost + ").");
            return;
        }

        MessageHelper.editMessageWithButtons(
                event,
                getGravecallDestroyMessage(player, requiredCost, newDestroyedCost),
                getGravecallDestroyShipButtons(player, tile, requiredCost, newDestroyedCost, newFightersDestroyed));
    }

    private static List<Button> getGravecallDestroyShipButtons(
            Player player, Tile tile, int requiredCost, float destroyedCost, int fightersDestroyed) {
        List<Button> buttons = new ArrayList<>();
        UnitHolder space = tile.getSpaceUnitHolder();
        for (UnitKey unitKey : space.getUnitKeysForPlayer(player)) {
            UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
            if (unitModel == null || !unitModel.getIsShip() || space.getUnitCount(unitKey) < 1) continue;
            boolean isFighter = unitKey.unitType() == UnitType.Fighter;
            float shipCost = isFighter ? (fightersDestroyed % 2 == 0 ? 1 : 0) : unitModel.getCost();
            String buttonID = player.factionButtonChecker() + DESTROY_OPPONENT_SHIP + tile.getPosition() + "|"
                    + requiredCost + "|" + destroyedCost + "|" + fightersDestroyed + "|" + unitKey.asyncID();
            buttons.add(Buttons.red(
                    buttonID,
                    "Destroy 1 " + unitKey.humanReadableName()
                            + (isFighter ? " (1x2 cost)" : " (cost " + formatCost(shipCost) + ")"),
                    unitKey.unitEmoji()));
        }
        return buttons;
    }

    private static String getGravecallDestroyMessage(Player player, int requiredCost, float destroyedCost) {
        return player.getRepresentationNoPing() + ", destroy ships with a total cost of " + requiredCost
                + " or more for _Gravecall_. Destroyed cost: " + formatCost(destroyedCost) + "/" + requiredCost + ".";
    }

    private static String formatCost(float cost) {
        return cost == Math.round(cost) ? Integer.toString(Math.round(cost)) : Float.toString(cost);
    }

    // Commander
    @ButtonHandler("gainAeternaCCOnLoss")
    public static void aeternaCommanderCCGain(ButtonInteractionEvent event, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Gain 1 CC due to _Vorun Kael_:", ButtonHelper.getGainCCButtons(player));

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    // Agent
    public static void startAeternaAgent(Game game, Player player) {
        List<Button> buttons = getAeternaAgentShipButtons(game, player);
        String prefix = player.factionButtonChecker() + SELECT_AGENT_SHIP;
        String message = player.getRepresentationNoPing() + ", choose a non-fighter ship to destroy for Morwen Deyth:";

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no eligible non-fighter ships.");
            return;
        }

        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        List<Button> paginatedButtons = NewStuffHelper.buttonPagination(buttons, extraButtons, prefix, 25, 0, false);
        if (buttons.size() <= 24) {
            paginatedButtons = new ArrayList<>(buttons);
            paginatedButtons.addAll(extraButtons);
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, paginatedButtons);
    }

    @ButtonHandler(SELECT_AGENT_SHIP)
    public static void resolveAeternaAgentShip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) return;

        List<Button> buttons = getAeternaAgentShipButtons(game, player);
        String message = player.getRepresentationNoPing() + ", choose a non-fighter ship to destroy for Morwen Deyth:";
        String prefix = player.factionButtonChecker() + SELECT_AGENT_SHIP;
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, extraButtons, message, prefix, buttonID)) {
            return;
        }

        String[] payload = buttonID.substring(SELECT_AGENT_SHIP.length()).split("\\|", 2);
        if (payload.length != 2) return;
        Tile sourceTile = game.getTileByPosition(payload[0]);
        UnitHolder sourceSpace = sourceTile == null ? null : sourceTile.getSpaceUnitHolder();
        UnitKey unitKey = sourceSpace == null
                ? null
                : sourceSpace.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(payload[1]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getUnitFromUnitKey(unitKey);
        if (unit == null || !unit.getIsShip() || unitKey.unitType() == UnitType.Fighter) return;

        DestroyUnitService.destroyUnit(event, sourceTile, game, unitKey, 1, sourceSpace, false);
        ButtonHelperStats.gainTGs(event, game, player, 2, true);

        List<Button> systemButtons = getAeternaAgentSystemButtons(game, player, sourceTile, unit.getBaseType());
        String resultMessage = player.getRepresentationNoPing() + " destroyed 1 " + unitKey.humanReadableName()
                + " and gained 2 trade goods from Morwen Deyth.";
        String systemMessage = player.getRepresentationNoPing() + " may place 1 neutral " + unit.getBaseType()
                + " in an eligible adjacent system:";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), resultMessage);
        if (systemButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), systemMessage + "\nThere are no eligible systems.");
        } else {
            String systemPrefix = player.factionButtonChecker() + SELECT_AGENT_SYSTEM + sourceTile.getPosition() + "|"
                    + unit.getBaseType() + "|";
            List<Button> systemExtraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
            List<Button> paginatedButtons =
                    NewStuffHelper.buttonPagination(systemButtons, systemExtraButtons, systemPrefix, 25, 0, false);
            if (systemButtons.size() <= 24) {
                paginatedButtons = new ArrayList<>(systemButtons);
                paginatedButtons.addAll(systemExtraButtons);
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), systemMessage, paginatedButtons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_AGENT_SYSTEM)
    public static void resolveAeternaAgentSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) return;

        String[] payload = buttonID.substring(SELECT_AGENT_SYSTEM.length()).split("\\|", 3);
        if (payload.length != 3) return;
        Tile sourceTile = game.getTileByPosition(payload[0]);
        if (sourceTile == null) return;

        List<Button> systemButtons = getAeternaAgentSystemButtons(game, player, sourceTile, payload[1]);
        String systemMessage = player.getRepresentationNoPing() + " may place 1 neutral " + payload[1]
                + " in an eligible adjacent system:";
        String systemPrefix = player.factionButtonChecker() + SELECT_AGENT_SYSTEM + payload[0] + "|" + payload[1] + "|";
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), systemButtons, extraButtons, systemMessage, systemPrefix, buttonID)) {
            return;
        }

        Tile destination = game.getTileByPosition(payload[2]);
        if (destination == null
                || !FoWHelper.getAdjacentTilesAndNotThisTile(game, sourceTile.getPosition(), player, false)
                        .contains(destination.getPosition())
                || destination.getTileModel().isHyperlane()
                || hasOtherPlayersShips(game, player, destination)) {
            return;
        }

        AddUnitService.addUnits(event, destination, game, game.getNeutral().getColor(), "1 " + payload[1]);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 neutral " + payload[1] + " in "
                        + destination.getRepresentationForButtons(game, player) + " with Morwen Deyth.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getAeternaAgentShipButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();

        for (Tile tile : game.getTileMap().values()) {
            UnitHolder space = tile.getSpaceUnitHolder();

            for (UnitKey unitKey : space.getUnitKeysForPlayer(player)) {
                UnitModel unit = player.getUnitFromUnitKey(unitKey);
                if (unit == null || !unit.getIsShip() || unitKey.unitType() == UnitType.Fighter) {
                    continue;
                }

                buttons.add(Buttons.green(
                        player.factionButtonChecker() + SELECT_AGENT_SHIP + tile.getPosition() + "|"
                                + unitKey.asyncID(),
                        "Destroy 1 " + unitKey.humanReadableName() + " in "
                                + tile.getRepresentationForButtons(game, player),
                        unitKey.unitEmoji()));
            }
        }

        return buttons;
    }

    private static List<Button> getAeternaAgentSystemButtons(
            Game game, Player player, Tile sourceTile, String unitType) {
        List<Button> buttons = new ArrayList<>();
        for (String position :
                FoWHelper.getAdjacentTilesAndNotThisTile(game, sourceTile.getPosition(), player, false)) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null || tile.getTileModel().isHyperlane() || hasOtherPlayersShips(game, player, tile)) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_AGENT_SYSTEM + sourceTile.getPosition() + "|" + unitType
                            + "|" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }

    private static boolean hasOtherPlayersShips(Game game, Player player, Tile tile) {
        return game.getRealPlayersNDummies().stream()
                .anyMatch(otherPlayer ->
                        otherPlayer != player && FoWHelper.playerHasActualShipsInSystem(otherPlayer, tile));
    }
}
