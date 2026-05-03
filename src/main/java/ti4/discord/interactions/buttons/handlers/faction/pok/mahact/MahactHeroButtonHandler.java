package ti4.discord.interactions.buttons.handlers.faction.pok.mahact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import ti4.helpers.FoWHelper;
import ti4.helpers.Units;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.fow.BlindSelectionService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
class MahactHeroButtonHandler {

    @ButtonHandler("mahactBenedictionFrom_")
    public static void mahactBenedictionFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        mahactBenediction(buttonID, event, game, player);
        String pos1 = buttonID.split("_")[1];
        String pos2 = buttonID.split("_")[2];
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmojiOrColor() + " moved all units in space from "
                        + game.getTileByPosition(pos1).getRepresentationForButtons(game, player) + " to "
                        + game.getTileByPosition(pos2).getRepresentationForButtons(game, player)
                        + " using Airo Shir Aur, the Mahact hero. If they moved themselves and wish to move ground forces, they may do so either with slash command or modify units button.");
        ButtonHelper.deleteMessage(event);
    }

    private static void mahactBenediction(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String pos1 = buttonID.split("_")[1];
        String pos2 = buttonID.split("_")[2];
        Tile tile1 = game.getTileByPosition(pos1);
        Tile tile2 = game.getTileByPosition(pos2);
        List<Player> players2 = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, game, tile1);
        if (!players2.isEmpty()) {
            player = players2.getFirst();
        }

        game.setStoredValue("mahactHeroTarget", player.getFaction());

        UnitHolder unitHolder = tile1.getUnitHolders().get("space");
        Map<Units.UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        for (Map.Entry<Units.UnitKey, Integer> unitEntry : units.entrySet()) {
            if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
            UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
            if (unitModel == null) continue;
            if (unitModel.getCapacityValue() < 1) {
                continue;
            }
            Units.UnitKey unitKey = unitEntry.getKey();
            String unitName = unitKey.unitName();
            int totalUnits = unitEntry.getValue();
            int damagedUnits = 0;

            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                damagedUnits = unitHolder.getUnitDamage().get(unitKey);
            }

            var parsedUnit = new ParsedUnit(unitKey, totalUnits, Constants.SPACE);
            RemoveUnitService.removeUnit(event, tile1, game, parsedUnit);
            AddUnitService.addUnits(event, tile2, game, player.getColor(), totalUnits + " " + unitName);
            if (damagedUnits > 0) {
                game.getTileByPosition(pos2).addUnitDamage("space", unitKey, damagedUnits);
            }
        }
        // this will catch all the capacity units left behind in the previous iteration
        for (Map.Entry<Units.UnitKey, Integer> unitEntry : units.entrySet()) {
            if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
            UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
            if (unitModel == null) continue;
            if (unitModel.getCapacityValue() > 0) {
                continue;
            }
            Units.UnitKey unitKey = unitEntry.getKey();
            String unitName = unitKey.unitName();
            int totalUnits = unitEntry.getValue();
            int damagedUnits = 0;

            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                damagedUnits = unitHolder.getUnitDamage().get(unitKey);
            }

            var parsedUnit = new ParsedUnit(unitKey, totalUnits, Constants.SPACE);
            RemoveUnitService.removeUnit(event, tile1, game, parsedUnit);
            AddUnitService.addUnits(event, tile2, game, player.getColor(), totalUnits + " " + unitName);
            if (damagedUnits > 0) {
                game.getTileByPosition(pos2).addUnitDamage(Constants.SPACE, unitKey, damagedUnits);
            }
        }

        List<Player> players = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, game, tile2);
        Player player2 = player;
        for (Player p2 : players) {
            if (p2 != player && !player.getAllianceMembers().contains(p2.getFaction())) {
                player2 = p2;
                break;
            }
        }
        if (player != player2) {
            StartCombatService.startSpaceCombat(game, player, player2, tile2, event, "-benediction");
        }
        game.setActiveSystem(pos2);
    }

    @ButtonHandler("benedictionStep1_")
    public static void benedictionStep1(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos1 = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " please choose the system you wish to send the ships in "
                        + game.getTileByPosition(pos1).getRepresentationForButtons(game, player) + " to.",
                getBenediction2ndTileOptions(player, game, pos1));
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getBenediction2ndTileOptions(Player player, Game game, String pos1) {
        String factionChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Player origPlayer = player;
        Tile tile1 = game.getTileByPosition(pos1);
        List<Player> players2 = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, game, tile1);
        if (!players2.isEmpty()) {
            player = players2.getFirst();
        }
        for (String pos2 : FoWHelper.getAdjacentTiles(game, pos1, player, false)) {
            if (pos1.equalsIgnoreCase(pos2)) {
                continue;
            }
            Tile tile2 = game.getTileByPosition(pos2);
            if (FoWHelper.otherPlayersHaveShipsInSystem(player, tile2, game)) {
                buttons.add(Buttons.gray(
                        factionChecker + "mahactBenedictionFrom_" + pos1 + "_" + pos2,
                        tile2.getRepresentationForButtons(game, origPlayer)));
            }
        }
        BlindSelectionService.filterForBlindPositionSelection(
                game, origPlayer, buttons, factionChecker + "mahactBenedictionFrom_" + pos1);
        return buttons;
    }
}
