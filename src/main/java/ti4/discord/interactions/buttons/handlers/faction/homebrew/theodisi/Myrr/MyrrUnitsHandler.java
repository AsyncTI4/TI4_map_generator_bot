package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Myrr;

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
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class MyrrUnitsHandler {
    private static final String DEPLOY_IRONBOUND = "deployIronboundGuardian_";
    private static final String PLACE_IRONBOUND = "placeIronboundGuardian_";

    // Replicators
    public static String getReplicatorProductionReminder(Player player, Tile tile) {
        int replicatorCount = 0;
        int replicatorProduction = 0;

        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnitKeys()) {
                if (!unitKey.getColor().equalsIgnoreCase(player.getColor())) {
                    continue;
                }

                UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), unitHolder);
                if (unit == null
                        || (!"myrr_dreadnought".equals(unit.getId()) && !"myrr_dreadnought2".equals(unit.getId()))) {
                    continue;
                }

                int count = unitHolder.getUnitCount(unitKey);
                replicatorCount += count;
                replicatorProduction += count * unit.getProductionValue();
            }
        }

        if (replicatorCount == 0) {
            return "";
        }

        return "-# You have " + replicatorCount + " Replicator"
                + (replicatorCount == 1 ? "" : "s")
                + " in this system with a total PRODUCTION of " + replicatorProduction + ". "
                + "These units may only produce dreadnoughts or unit types they transported.\n";
    }

    public static void offerIronboundGuardianDeploy(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        if (!canDeployIronboundGuardian(game, player, tile)) {
            return;
        }

        UnitHolder space = tile.getSpaceUnitHolder();
        int fighters = space.getUnitCount(UnitType.Fighter, player);
        int infantry = space.getUnitCount(UnitType.Infantry, player);
        List<Button> buttons = new ArrayList<>();
        if (fighters >= 2) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + DEPLOY_IRONBOUND + tile.getPosition() + "|2|0",
                    "Remove 2 Fighters",
                    FactionEmojis.myrr));
        }
        if (fighters >= 1 && infantry >= 1) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + DEPLOY_IRONBOUND + tile.getPosition() + "|1|1",
                    "Remove 1 Fighter and 1 Infantry",
                    FactionEmojis.myrr));
        }
        if (infantry >= 2) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + DEPLOY_IRONBOUND + tile.getPosition() + "|0|2",
                    "Remove 2 Infantry",
                    FactionEmojis.myrr));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", you may use an Ironbound Guardian (Myrr mech)'s DEPLOY ability after movement.",
                buttons);
    }

    @ButtonHandler(DEPLOY_IRONBOUND)
    public static void deployIronboundGuardian(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(DEPLOY_IRONBOUND.length()).split("\\|", 3);
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        int fighters;
        int infantry;
        try {
            fighters = payload.length == 3 ? Integer.parseInt(payload[1]) : -1;
            infantry = payload.length == 3 ? Integer.parseInt(payload[2]) : -1;
        } catch (NumberFormatException ignored) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        UnitHolder space = tile == null ? null : tile.getSpaceUnitHolder();
        if (!canDeployIronboundGuardian(game, player, tile)
                || fighters < 0
                || infantry < 0
                || fighters + infantry != 2
                || space.getUnitCount(UnitType.Fighter, player) < fighters
                || space.getUnitCount(UnitType.Infantry, player) < infantry) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (fighters > 0) {
            RemoveUnitService.removeUnit(event, tile, game, player, space, UnitType.Fighter, fighters);
        }
        if (infantry > 0) {
            RemoveUnitService.removeUnit(event, tile, game, player, space, UnitType.Infantry, infantry);
        }

        List<Button> buttons = getIronboundPlacementButtons(game, player, tile);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " removed 2 units for an Ironbound Guardian (Myrr mech)'s DEPLOY ability, but has no units remaining in the active system with which to place it.");
        } else {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + ", please choose where to place 1 Ironbound Guardian (Myrr mech) with your units.",
                    buttons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_IRONBOUND)
    public static void placeIronboundGuardian(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PLACE_IRONBOUND.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(payload[1]);
        if (tile == null
                || holder == null
                || !player.hasUnit("myrr_mech")
                || !tile.getPosition().equals(game.getActiveSystem())
                || holder.countPlayersUnitsWithModelCondition(player, unit -> true) < 1
                || !hasMechInReinforcements(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mf " + holder.getName());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed 1 Ironbound Guardian (Myrr mech) using its DEPLOY ability.");
        ButtonHelper.deleteMessage(event);
    }

    private static boolean canDeployIronboundGuardian(Game game, Player player, Tile tile) {
        if (game == null
                || player == null
                || tile == null
                || !player.hasUnit("myrr_mech")
                || !tile.getPosition().equals(game.getActiveSystem())
                || ButtonHelper.isLawInPlay(game, "articles_war")
                || !hasMechInReinforcements(game, player)) {
            return false;
        }
        UnitHolder space = tile.getSpaceUnitHolder();
        int removable = space.getUnitCount(UnitType.Fighter, player) + space.getUnitCount(UnitType.Infantry, player);
        int totalUnits = tile.getUnitHolders().values().stream()
                .mapToInt(holder -> holder.countPlayersUnitsWithModelCondition(player, unit -> true))
                .sum();
        return removable >= 2 && totalUnits > 2;
    }

    private static boolean hasMechInReinforcements(Game game, Player player) {
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mf") < player.getUnitCap("mf");
    }

    private static List<Button> getIronboundPlacementButtons(Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            if (holder.countPlayersUnitsWithModelCondition(player, unit -> true) < 1) {
                continue;
            }
            String location =
                    Constants.SPACE.equals(holder.getName()) ? "Space" : Helper.getPlanetName(holder.getName());
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_IRONBOUND + tile.getPosition() + "|" + holder.getName(),
                    "Place on " + location,
                    FactionEmojis.myrr));
        }
        return buttons;
    }
}
