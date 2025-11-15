package ti4.service.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.regex.RegexService;

public class GalvanizeService {

    @ButtonHandler("getToggleGalvanizeTiles")
    public static void postToggleGalvanizeTiles(Game game, Player player) {
        List<Button> buttons = ButtonHelper.getTilesWithUnitsForAction(player, game, "toggleGalvanize", true);
        String message = player.getRepresentationUnfogged()
                + " Use the buttons to select the tile in which you wish to modify units. ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("toggleGalvanize_")
    public static void postToggleGalvanizeButtons(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.replace("toggleGalvanize_", ""));
        List<Button> buttons = getToggleGalvanizeButtons(player, game, tile);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), player.getRepresentation(true, true) + " Use buttons to resolve", buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getToggleGalvanizeButtons(Player player, Game game, Tile tile) {
        String finChecker = player.getFinsFactionCheckerPrefix();
        List<Button> buttons = new ArrayList<>();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        String pos = tile.getPosition() + "_";
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) representation = name;

            UnitHolder unitHolder = entry.getValue();
            for (UnitKey unit : unitHolder.getUnitsByState().keySet()) {
                int total = unitHolder.getUnitCount(unit);
                if (!player.unitBelongsToPlayer(unit) || total <= 0) {
                    continue;
                }
                int galvanized = unitHolder.getGalvanizedUnitCount(unit);
                int ungalvanized = total - galvanized;

                String unitIdPart = pos + unit.asyncID() + "_" + unitHolder.getName();
                if (ungalvanized > 0)
                    buttons.add(Buttons.green(
                            finChecker + "galvanize_" + unitIdPart,
                            "Galvanize 1 " + unit.unitName(),
                            unit.unitEmoji()));
                if (galvanized > 0)
                    buttons.add(Buttons.red(
                            finChecker + "ungalvanize_" + unitIdPart,
                            "Ungalvanize 1 " + unit.unitName(),
                            unit.unitEmoji()));
            }
        }
        buttons.add(Buttons.blue("deleteButtons", "Done galvanizing units"));
        return buttons;
    }

    @ButtonHandler("ungalvanize_") // ungalvanize_pos_unit(_planet)?
    public static void ungalvanize(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "ungalvanize_" + RegexHelper.posRegex(game) + "_" + RegexHelper.unitTypeRegex() + "_"
                + RegexHelper.unitHolderRegex(game, "holder");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            UnitKey unitKey = Mapper.getUnitKey(matcher.group("unittype"), player.getColorID());
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            UnitHolder uh = tile.getUnitHolders().get(matcher.group("holder"));
            resolveGalvanize(event, game, player, tile, uh, unitKey, false);
        });
    }

    @ButtonHandler("galvanize_")
    public static void galvanize(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "galvanize_" + RegexHelper.posRegex(game) + "_" + RegexHelper.unitTypeRegex() + "_"
                + RegexHelper.unitHolderRegex(game, "holder");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            UnitKey unitKey = Mapper.getUnitKey(matcher.group("unittype"), player.getColorID());
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            UnitHolder uh = tile.getUnitHolders().get(matcher.group("holder"));
            resolveGalvanize(event, game, player, tile, uh, unitKey, true);
            CommanderUnlockCheckService.checkAllPlayersInGame(game, "bastion");
        });
    }

    private static void resolveGalvanize(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            Tile tile,
            UnitHolder uh,
            UnitKey unit,
            boolean add) {
        String uhName = uh.getRepresentation(game), grammar = " in ";
        if (uh instanceof Planet) grammar = " on ";
        if (add) uh.addGalvanizedUnit(unit, 1);
        if (!add) uh.removeGalvanizedUnit(unit, 1);
        refreshGalvanizeButtons(event, game, player, tile);
        String descr = unit.getUnitType().humanReadableName() + grammar + uhName;
        String addRemove = add ? " galvanized " : " removed galvanize from ";
        String msg = player.getRepresentation() + addRemove + descr + " in tile "
                + tile.getRepresentationForButtons(game, player);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    // TODO: Jazz make it use paginator instead
    private static void refreshGalvanizeButtons(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        List<Button> systemButtons = getToggleGalvanizeButtons(player, game, tile);
        if (systemButtons.size() > 25) systemButtons = systemButtons.subList(0, 25);
        event.getMessage()
                .editMessageComponents(ActionRow.partitionOf(systemButtons))
                .queue();
    }
}
