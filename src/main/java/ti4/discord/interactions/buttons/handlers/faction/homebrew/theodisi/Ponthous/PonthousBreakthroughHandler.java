package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ponthous;

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
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class PonthousBreakthroughHandler {
    private static final String SELF_DESTRUCT = "ponthousSelfDestruct_";
    private static final String SELF_DESTRUCT_SHIP = "ponthousSelfDestructShip_";
    private static final String SELF_DESTRUCT_GROUND_TARGET = "ponthousSelfDestructGroundTarget_";

    public static Button getSelfDestructButton(Player player, Tile tile, String combatType) {
        boolean hasEligibleShip = tile.getSpaceUnitHolder().getUnitKeys().stream()
                .filter(player::unitBelongsToPlayer)
                .anyMatch(unitKey -> {
                    UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                    return unitModel != null && unitModel.getIsShip() && unitKey.unitType() != UnitType.Fighter;
                });

        if (!hasEligibleShip) {
            return null;
        }

        return Buttons.red(
                player.factionButtonChecker() + SELF_DESTRUCT + tile.getPosition() + "_" + combatType,
                "Use Self-Destruct Button",
                FactionEmojis.ponthous);
    }

    @ButtonHandler(SELF_DESTRUCT)
    public static void chooseSelfDestructShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.split("_", 3);
        String position = parts[1];
        String combatType = parts[2];
        Tile tile = game.getTileByPosition(position);

        if (!player.hasReadyBreakthrough("ponthousbt")
                || tile == null
                || !position.equals(game.getCurrentActiveSystem())
                || !("space".equalsIgnoreCase(combatType) || "ground".equalsIgnoreCase(combatType))) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : tile.getSpaceUnitHolder().getUnitKeys()) {
            if (!player.unitBelongsToPlayer(unitKey) || unitKey.unitType() == UnitType.Fighter) {
                continue;
            }

            UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
            if (unitModel == null || !unitModel.getIsShip()) {
                continue;
            }

            int hits = (int) unitModel.getCost();
            buttons.add(Buttons.red(
                    player.factionButtonChecker()
                            + SELF_DESTRUCT_SHIP
                            + position
                            + "_"
                            + combatType
                            + "_"
                            + unitKey.asyncID(),
                    "Destroy 1 " + unitKey.humanReadableName() + " (" + hits + " hits)",
                    unitKey.unitEmoji()));
        }

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);

        if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + ", choose the non-fighter ship to destroy with _Self-Destruct Button_.",
                    buttons);
        }
    }

    @ButtonHandler(SELF_DESTRUCT_SHIP)
    public static void resolveSelfDestruct(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.split("_", 4);
        resolveSelfDestruct(event, game, player, parts[1], parts[2], parts[3], null);
    }

    @ButtonHandler(SELF_DESTRUCT_GROUND_TARGET)
    public static void resolveSelfDestructGroundTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace(SELF_DESTRUCT_GROUND_TARGET, "").split("\\|", 3);
        resolveSelfDestruct(event, game, player, parts[0], "ground", parts[1], parts[2]);
    }

    private static void resolveSelfDestruct(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            String position,
            String combatType,
            String unitId,
            String targetPlanet) {

        Tile tile = game.getTileByPosition(position);
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitId), player.getColor());
        UnitModel unitModel = unitKey == null ? null : player.getUnitFromUnitKey(unitKey);

        if (!player.hasReadyBreakthrough("ponthousbt")
                || tile == null
                || !position.equals(game.getCurrentActiveSystem())
                || unitModel == null
                || !unitModel.getIsShip()
                || unitKey.unitType() == UnitType.Fighter
                || tile.getSpaceUnitHolder().getUnitCount(unitKey) < 1
                || !("space".equalsIgnoreCase(combatType) || "ground".equalsIgnoreCase(combatType))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Player> combatPlayers = new ArrayList<>();
        for (String faction : game.getStoredValue("factionsInCombat").split("_")) {
            Player combatPlayer = game.getPlayerFromColorOrFaction(faction);
            if (combatPlayer != null) {
                combatPlayers.add(combatPlayer);
            }
        }
        if (!combatPlayers.contains(player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player opponent = null;
        UnitHolder combatHolder = tile.getSpaceUnitHolder();
        if ("ground".equalsIgnoreCase(combatType)) {
            List<UnitHolder> groundCombatHolders = new ArrayList<>();
            for (UnitHolder holder : tile.getPlanetUnitHolders()) {
                boolean playerHasUnits = holder.getUnitKeys().stream().anyMatch(player::unitBelongsToPlayer);
                if (!playerHasUnits) {
                    continue;
                }
                for (UnitKey otherUnit : holder.getUnitKeys()) {
                    Player candidate = game.getPlayerFromColorOrFaction(otherUnit.getColor());
                    if (candidate != null && candidate != player && combatPlayers.contains(candidate)) {
                        groundCombatHolders.add(holder);
                        break;
                    }
                }
            }

            if (targetPlanet == null && groundCombatHolders.size() > 1) {
                List<Button> targetButtons = new ArrayList<>();
                int hits = (int) unitModel.getCost();
                for (UnitHolder holder : groundCombatHolders) {
                    targetButtons.add(Buttons.red(
                            player.factionButtonChecker() + SELF_DESTRUCT_GROUND_TARGET
                                    + position
                                    + "|"
                                    + unitId
                                    + "|"
                                    + holder.getName(),
                            "Produce " + hits + " hits on " + Helper.getPlanetRepresentation(holder.getName(), game)));
                }
                ButtonHelper.deleteMessage(event);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation() + ", choose the ground combat for _Self-Destruct Button_.",
                        targetButtons);
                return;
            }

            combatHolder = targetPlanet == null
                    ? (groundCombatHolders.isEmpty() ? null : groundCombatHolders.getFirst())
                    : tile.getUnitHolders().get(targetPlanet);
            if (combatHolder == null || !groundCombatHolders.contains(combatHolder)) {
                ButtonHelper.deleteMessage(event);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getRepresentation() + ", no opposing units could be found in that ground combat.");
                return;
            }
        }
        for (UnitKey otherUnit : combatHolder.getUnitKeys()) {
            Player candidate = game.getPlayerFromColorOrFaction(otherUnit.getColor());
            if (candidate != null && candidate != player && combatPlayers.contains(candidate)) {
                opponent = candidate;
                break;
            }
        }

        if (opponent == null) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", no opposing units could be found in that combat.");
            return;
        }

        player.setBreakthroughExhausted("ponthousbt", true);
        int hits = (int) unitModel.getCost();
        DestroyUnitService.destroyUnit(event, tile, game, new ParsedUnit(unitKey), true);

        ButtonHelper.deleteMessage(event);

        String message = player.getRepresentation()
                + " exhausted _Self-Destruct Button_ and destroyed 1 "
                + unitKey.humanReadableName()
                + " to produce "
                + hits
                + " hit"
                + (hits == 1 ? "" : "s")
                + ".";

        List<Button> hitButtons = new ArrayList<>();
        boolean groundCombat = "ground".equalsIgnoreCase(combatType);
        if (groundCombat) {
            hitButtons.add(Buttons.green(
                    opponent.factionButtonChecker() + "autoAssignGroundHits_" + combatHolder.getName() + "_" + hits,
                    "Auto-assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
            hitButtons.add(Buttons.red(
                    opponent.factionButtonChecker() + "getDamageButtons_" + position + "deleteThis_groundcombat",
                    "Manually Assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
        } else {
            hitButtons.add(Buttons.green(
                    opponent.factionButtonChecker() + "autoAssignSpaceHits_" + position + "_" + hits,
                    "Auto-assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
            hitButtons.add(Buttons.red(
                    opponent.factionButtonChecker() + "getDamageButtons_" + position + "deleteThis_spacecombat",
                    "Manually Assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
        }

        message += "\n" + opponent.getRepresentationUnfogged() + ", assign the produced hits.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, hitButtons);
    }
}
