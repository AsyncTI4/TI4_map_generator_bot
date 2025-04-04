package ti4.helpers.async;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.collections4.ListUtils;
import org.jetbrains.annotations.Nullable;
import ti4.buttons.Buttons;
import ti4.helpers.RegexHelper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.TileModel;

// Jazz's Interactive Map Builder
public class JimboButtons {
    // Main Page
    public static final Button MAIN_PAGE = Buttons.gray(JimboConst.mainPage, "Go back to main menu");
    public static final Button EXIT = Buttons.red(JimboConst.exit, "Exit");

    // Tiles: red/blue/green/hyperlane/draft/{other?}
    public static final Button TILE_ACTION = Buttons.blue(JimboConst.tileAction, "Adjust tiles");
    public static final Button TILE_ADD = Buttons.green(JimboConst.tileAdd, "Add a tile");
    public static final Button TILE_MOVE = Buttons.blue(JimboConst.tileRemove, "Remove a tile");
    public static final Button TILE_REMOVE = Buttons.red(JimboConst.tileMove, "Move a tile");

    // Features : border anomalies and tokens / attachments
    public static final Button FEATURE_ACTION = Buttons.blue(JimboConst.featureAction, "Adjust tokens");
    public static final Button TOKEN_ADD = Buttons.green(JimboConst.tokenAdd, "Add a token");
    public static final Button TOKEN_REMOVE = Buttons.red(JimboConst.tokenRemove, "Remove a token");
    public static final Button BORDER_ADD = Buttons.green(JimboConst.borderAdd, "Add a border anomaly");
    public static final Button BORDER_REMOVE = Buttons.red(JimboConst.borderRemove, "Remove a border anomaly");

    // Transformations: Rotate, Translate
    public static final Button TRANSFORM_ACTION = Buttons.blue(JimboConst.transformAction, "Transform the map (rotate/shift)");
    public static final Button TRANSFORM_ROTATE_CW = Buttons.green(JimboConst.transformRotate + "cw", "Rotate clockwise"); //. . . . . . . TODO (Jazz): add emoji
    public static final Button TRANSFORM_ROTATE_CCW = Buttons.green(JimboConst.transformRotate + "ccw", "Rotate counter-clockwise"); //. . TODO (Jazz): add emoji
    public static final Button TRANSFORM_TRANSLATE_0 = Buttons.blue(JimboConst.transformTranslate + "0", "Shift the map UP"); // . . . . . TODO (Jazz): add emoji
    public static final Button TRANSFORM_TRANSLATE_1 = Buttons.blue(JimboConst.transformTranslate + "1", "Shift the map UP & RIGHT"); // . TODO (Jazz): add emoji
    public static final Button TRANSFORM_TRANSLATE_2 = Buttons.blue(JimboConst.transformTranslate + "2", "Shift the map DOWN & RIGHT"); // TODO (Jazz): add emoji
    public static final Button TRANSFORM_TRANSLATE_3 = Buttons.blue(JimboConst.transformTranslate + "3", "Shift the map DOWN"); // . . . . TODO (Jazz): add emoji
    public static final Button TRANSFORM_TRANSLATE_4 = Buttons.blue(JimboConst.transformTranslate + "4", "Shift the map DOWN & LEFT"); //. TODO (Jazz): add emoji
    public static final Button TRANSFORM_TRANSLATE_5 = Buttons.blue(JimboConst.transformTranslate + "5", "Shift the map UP & LEFT"); //. . TODO (Jazz): add emoji

    // Meta Actions: Add symmetry (and other settings TBD)
    public static final Button META_ACTION = Buttons.blue(JimboConst.metaAction, "Adjust map settings");
    public static final Button META_SYMMETRY_ADD = Buttons.green(JimboConst.metaSymmetryAdd, "Add Symmetry");
    public static final Button META_SYMMETRY_REMOVE = Buttons.green(JimboConst.metaSymmetryRemove, "Remove Symmetry");

    public static List<Button> getMenuButtons(String submenu) {
        if (submenu == null) return null;
        List<Button> buttons = switch (submenu) {
            case JimboConst.mainPage -> List.of(TILE_ACTION, FEATURE_ACTION, TRANSFORM_ACTION, META_ACTION, EXIT);
            case JimboConst.featureAction -> List.of(MAIN_PAGE, TOKEN_ADD, TOKEN_REMOVE, BORDER_ADD, BORDER_REMOVE);
            case JimboConst.tileAction -> List.of(MAIN_PAGE, TILE_ADD, TILE_MOVE, TILE_REMOVE);
            case JimboConst.transformAction -> List.of(MAIN_PAGE, TRANSFORM_ROTATE_CW, TRANSFORM_ROTATE_CCW, TRANSFORM_TRANSLATE_0, TRANSFORM_TRANSLATE_1, TRANSFORM_TRANSLATE_2, TRANSFORM_TRANSLATE_3, TRANSFORM_TRANSLATE_4, TRANSFORM_TRANSLATE_5);
            case JimboConst.metaAction -> List.of(MAIN_PAGE, META_SYMMETRY_ADD, META_SYMMETRY_REMOVE);
            default -> List.of();
        };
        return new ArrayList<>(buttons);
    }

    public static Button tileToButton(TileModel tile, String prefix) {
        String id = prefix + "_" + tile.getAlias();
        String label = tile.getName() + " (" + tile.getAlias() + ")";
        String emoji = tile.getSource() == null ? null : tile.getSource().emoji();
        return Buttons.green(id, label, emoji);
    }

    public static Button positionToButton(String position, String prefix, Game game) {
        String id = prefix + "_pos" + position;
        String label = position;
        Tile existing = game.getTileByPosition(position);
        if (existing != null) {
            label = "(" + existing.getRepresentationForButtons(game, null) + ")";
            if (TileHelper.isDraftTile(existing.getTileModel()))
                return Buttons.gray(id, label);
            return Buttons.red(id, label);
        }
        return Buttons.green(id, label);
    }

    public static <T> boolean jimboPagination(ButtonInteractionEvent event, String msg, List<T> all, Function<T, Button> buttonator, @Nullable Function<List<T>, FileUpload> uploadinator, @Nullable List<Button> bonus, int size, String buttonID) {
        try {
            int pagenum;
            String prefix;
            System.out.println("pagination: " + all.size() + " - " + buttonID);
            Matcher page = Pattern.compile(RegexHelper.pageRegex()).matcher(buttonID);
            if (!page.find()) return false; // no pagenum, don't paginate
            pagenum = Integer.parseInt(page.group("page"));
            prefix = buttonID.replace("_page" + pagenum, "");

            List<List<T>> pages = ListUtils.partition(all, size);
            if (pagenum >= pages.size()) pagenum = pages.size() - 1;
            if (pagenum < 0) pagenum = 0;
            List<T> pageToSend = pages.get(pagenum);
            List<ActionRow> rowsToSend = getActionRowsForPage(pageToSend, bonus, buttonator, prefix, pagenum, pages.size() - 1);
            List<FileUpload> listToSend = uploadinator == null ? Collections.emptyList() : List.of(uploadinator.apply(pageToSend));
            MessageHelper.editMessageWithActionRowsAndFiles(event, msg, rowsToSend, listToSend);
            return true; // no further actions needed
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(event), "Unexpected exception in JIMBO pagination:", e);
            return true; // we still want to abort any further actions
        }
    }

    private static <T> List<ActionRow> getActionRowsForPage(List<T> page, List<Button> bonusButts, Function<T, Button> buttonator, String prefix, int pagenum, int maxPage) {
        List<Button> buttons = new ArrayList<>(bonusButts);
        if (pagenum > 0) buttons.add(Buttons.blue(prefix + "_page" + (pagenum - 1), "Previous Page", "⏪"));
        if (pagenum < maxPage) buttons.add(Buttons.blue(prefix + "_page" + (pagenum + 1), "Next Page", "⏩"));

        List<ActionRow> rows = new ArrayList<>(List.of(ActionRow.of(buttons)));
        buttons.clear();
        page.stream().map(buttonator).forEach(buttons::add);
        rows.addAll(ActionRow.partitionOf(buttons));
        return rows;
    }
}
