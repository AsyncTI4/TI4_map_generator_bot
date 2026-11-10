package ti4.service.map;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.SortHelper;
import ti4.helpers.adjacency.GridRule;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.HyperlaneStyle;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;

@UtilityClass
public class CustomHyperlaneService {

    private static final String HYPERLANE_TILEID = "hl";
    private static final String ONE_WAY = "2";
    private static final List<String> SIDE_NAMES = List.of("N", "NE", "SE", "S", "SW", "NW");

    private static final List<Button> HYPERLANE_BUTTONS = Arrays.asList(
            Buttons.gray("customHyperlaneRefresh", "Refresh"),
            Buttons.gray("customHyperlaneMore", "More"),
            Buttons.DONE_DELETE_BUTTONS);

    private static final List<Button> HYPERLANE_MORE_BUTTONS = Arrays.asList(
            Buttons.gray("customHyperlaneImport~MDL", "Import", "⬇️"),
            Buttons.gray("customHyperlaneExport", "Export", "⬆️"),
            Buttons.gray("customHyperlaneTransform~MDL", "Transform", "↔️"),
            Buttons.gray("customHyperlaneStyle", "One-Way Style", "🎨"),
            Buttons.DONE_DELETE_BUTTONS);

    public static boolean isCustomHyperlaneTile(Tile tile) {
        return HYPERLANE_TILEID.equals(tile.getTileID());
    }

    public static void offerManageHyperlaneButtons(Game game, GenericInteractionCreateEvent event) {
        offerManageHyperlaneButtons(game, event, null);
    }

    @ButtonHandler(value = "customHyperlanePagination", save = false)
    public static void offerManageHyperlaneButtons(ButtonInteractionEvent event, String buttonID, Game game) {
        offerManageHyperlaneButtons(game, event, buttonID);
    }

    // Red button    has hyperlane data on invalid tile
    // Green button  has existing hyperlane data
    // Blue button   empty hyperlane tile without data
    private static void offerManageHyperlaneButtons(Game game, GenericInteractionCreateEvent event, String buttonID) {
        String page = StringUtils.substringAfter(buttonID, "page");
        int pageNum = StringUtils.isBlank(page) ? 1 : Integer.parseInt(page);
        List<Button> hyperlaneTileButtons = getHyperlaneButtons(game);
        List<ActionRow> buttons =
                Buttons.paginateButtons(hyperlaneTileButtons, HYPERLANE_BUTTONS, pageNum, "customHyperlanePagination");

        if (StringUtils.isBlank(page)) {
            StringBuilder sb = new StringBuilder("### Manage Custom Hyperlanes");
            if (hyperlaneTileButtons.isEmpty()) {
                sb.append("\nNo hyperlane tiles found. Use `/map add_tile tile_name:")
                        .append(HYPERLANE_TILEID)
                        .append("` to add.");
            }
            event.getMessageChannel()
                    .sendMessage(sb.toString())
                    .setComponents(buttons)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        } else {
            ((IDeferrableCallback) event)
                    .getHook()
                    .editOriginalComponents(buttons)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    private static List<Button> getHyperlaneButtons(Game game) {
        Map<String, String> customHyperlaneData = game.getCustomHyperlaneData();
        List<Button> hyperlaneTileButtons = new ArrayList<>();
        for (String position : customHyperlaneData.keySet()) {
            Tile tileWithExistingData = game.getTileByPosition(position);
            hyperlaneTileButtons.add(
                    tileWithExistingData == null || !isCustomHyperlaneTile(tileWithExistingData)
                            ? Buttons.red("customHyperlaneEdit_" + position + "~MDL", position)
                            : Buttons.green("customHyperlaneEdit_" + position + "~MDL", position));
        }
        for (Entry<String, Tile> entry : game.getTileMap().entrySet()) {
            String position = entry.getKey();
            if (isCustomHyperlaneTile(entry.getValue())
                    && !game.getCustomHyperlaneData().containsKey(position)) {
                hyperlaneTileButtons.add(Buttons.blue("customHyperlaneEdit_" + position + "~MDL", position));
            }
        }
        SortHelper.sortButtonsByTitle(hyperlaneTileButtons);
        return hyperlaneTileButtons;
    }

    @ButtonHandler(value = "customHyperlaneRefresh", save = false)
    public static void refreshHyperlaneButtons(ButtonInteractionEvent event, Game game) {
        offerManageHyperlaneButtons(game, event, null);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler(value = "customHyperlaneMore", save = false)
    public static void moreHyperlaneButtons(ButtonInteractionEvent event) {
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "", HYPERLANE_MORE_BUTTONS);
    }

    @ButtonHandler(value = "customHyperlaneStyle", save = false)
    public static void offerHyperlaneStyles(ButtonInteractionEvent event, Game game) {
        HyperlaneStyle current = HyperlaneStyleService.gameDefault(game);
        List<Button> buttons = new ArrayList<>();
        for (HyperlaneStyle style : HyperlaneStyle.values()) {
            String id = "customHyperlaneSetStyle_" + style.name();
            buttons.add(style == current ? Buttons.green(id, style.getLabel()) : Buttons.gray(id, style.getLabel()));
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                "How one-way hyperlanes look on this game's shared map. Players can pick their own view for maps"
                        + " drawn just for them with `/user set_preferred_settings custom_hyperlane_style`.",
                buttons);
    }

    @ButtonHandler("customHyperlaneSetStyle_")
    public static void setHyperlaneStyle(ButtonInteractionEvent event, String buttonID, Game game) {
        HyperlaneStyle.fromName(StringUtils.substringAfter(buttonID, "customHyperlaneSetStyle_"))
                .ifPresent(style -> {
                    HyperlaneStyleService.setGameDefault(game, style);
                    MessageHelper.sendMessageToChannel(
                            event.getChannel(), "One-way hyperlanes now use **" + style.getLabel() + "**.");
                });
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(value = "customHyperlaneExport", save = false)
    public static void exportHyperlaneData(ButtonInteractionEvent event, Game game) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : game.getCustomHyperlaneData().entrySet()) {
            sb.append(entry.getKey())
                    .append(",")
                    .append(encodeMatrix(entry.getValue()))
                    .append(' ');
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }

    @ButtonHandler(value = "customHyperlaneImport~MDL", save = false)
    public static void importHyperlaneData(ButtonInteractionEvent event) {
        TextInput.Builder data = TextInput.create(Constants.SETTING_VALUE, TextInputStyle.PARAGRAPH);

        Modal importDataModal = Modal.create("customHyperlaneImportSave", "Import Data (overwrites existing)")
                .addComponents(Label.of("Hyperlane Data", data.build()))
                .build();

        event.replyModal(importDataModal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("customHyperlaneImportSave")
    public static void saveImportedHyperlaneData(ModalInteractionEvent event, Game game) {
        String importData =
                event.getValue(Constants.SETTING_VALUE).getAsString().replace("\n", " ");

        Map<String, String> customHyperlaneData = new HashMap<>();
        for (String dataRow : importData.split(" ")) {
            String[] data = dataRow.split(",");
            if (data.length != 2) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid data: `" + dataRow + "`");
                return;
            }

            String position = data[0];
            if (!MapPositionMapper.isTilePositionValid(game, position)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid position: `" + position + "`");
                return;
            }

            String matrix = decodeMatrix(data[1].trim());
            if (!isValidStoredMatrix(matrix)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid connection matrix: `" + matrix + "`");
                return;
            }

            customHyperlaneData.put(position, matrix);
        }

        if (!customHyperlaneData.isEmpty()) {
            game.setCustomHyperlaneData(customHyperlaneData);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Hyperlane data imported.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Found nothing to import.");
        }
    }

    @ButtonHandler("customHyperlaneEdit")
    public static void editHyperlaneData(ButtonInteractionEvent event, String buttonID, Game game) {
        String position = StringUtils.substringBetween(buttonID, "customHyperlaneEdit_", "~MDL");

        TextInput.Builder data = TextInput.create(Constants.SETTING_VALUE, TextInputStyle.PARAGRAPH)
                .setPlaceholder("0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0")
                .setValue("0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0")
                .setRequired(false)
                .setMaxLength(76);

        Map<String, String> customHyperlaneData = game.getCustomHyperlaneData();
        if (customHyperlaneData.containsKey(position)) {
            data.setValue(toTypedMatrix(customHyperlaneData.get(position)).replace(";", ";\n"));
        }

        Modal customHyperlaneModal = Modal.create("customHyperlaneSave_" + position, position + " Hyperlane")
                .addComponents(Label.of("Matrix: 1 two-way, 2 one-way, clear deletes", data.build()))
                .build();

        event.replyModal(customHyperlaneModal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("customHyperlaneSave_")
    public static void saveHyperlaneData(ModalInteractionEvent event, Game game) {
        String[] modalId = event.getModalId().split("_");
        String position = modalId[1];
        String hyperlaneData =
                event.getValue(Constants.SETTING_VALUE).getAsString().replace("\n", "");

        Map<String, String> customHyperlaneData = game.getCustomHyperlaneData();
        if (StringUtils.isBlank(hyperlaneData)) {
            customHyperlaneData.remove(position);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Hyperlane data removed from " + position + ".");
        } else {
            if (!isValidConnectionMatrix(hyperlaneData)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Data `" + hyperlaneData + "` is invalid.");
                return;
            }

            insertData(game, position, hyperlaneData);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Hyperlane data `" + hyperlaneData + "` added to " + position + "."
                            + oneWayLanesLine(customHyperlaneData.get(position))
                            + contradictionsLine(hyperlaneData));
            if (hasOneWayLanes(customHyperlaneData.get(position))) {
                OneWayAdjacencyHintService.hintOnce(game, event.getChannel());
            }
        }
    }

    private static String oneWayLanesLine(String stored) {
        List<String> lanes = describeOneWayLanes(stored);
        return lanes.isEmpty() ? "" : "\nOne-way: " + String.join(", ", lanes) + ".";
    }

    private static String contradictionsLine(String typed) {
        List<String> lanes = describeContradictions(typed);
        return lanes.isEmpty()
                ? ""
                : "\n-# Typed in both directions, so saved as two-way: " + String.join(", ", lanes) + ".";
    }

    public static List<String> describeContradictions(String typed) {
        int[][] grid = parseMatrix(typed);
        List<String> lanes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            for (int j = i + 1; j < 6; j++) {
                boolean oneWayTyped = grid[i][j] == 2 || grid[j][i] == 2;
                if (oneWayTyped && grid[i][j] != 0 && grid[j][i] != 0) {
                    lanes.add(SIDE_NAMES.get(i) + " ↔ " + SIDE_NAMES.get(j));
                }
            }
        }
        return lanes;
    }

    public static void insertData(Game game, String position, String hyperlaneData) {
        game.getCustomHyperlaneData().put(position, toStoredMatrix(hyperlaneData));
    }

    public static void insertStoredData(Game game, String position, String storedData) {
        game.getCustomHyperlaneData().put(position, storedData);
    }

    @ButtonHandler(value = "customHyperlaneTransform~MDL", save = false)
    public static void transformHyperlane(ButtonInteractionEvent event) {
        TextInput.Builder data1 = TextInput.create("staticToCustom", TextInputStyle.SHORT)
                .setPlaceholder("Comma separated positions or ALL")
                .setRequired(false);
        TextInput.Builder data2 = TextInput.create("customToStatic", TextInputStyle.SHORT)
                .setPlaceholder("Comma separated positions or ALL")
                .setRequired(false);

        Modal modal = Modal.create("customHyperlaneTransformExecute", "Transform Hyperlanes")
                .addComponents(Label.of("Static -> Custom", data1.build()), Label.of("Custom -> Static", data2.build()))
                .build();

        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("customHyperlaneTransformExecute")
    public static void transformHyperlaneExecute(ModalInteractionEvent event, Game game) {
        String staticToCustom = event.getValue("staticToCustom").getAsString();
        String customToStatic = event.getValue("customToStatic").getAsString();

        StringBuilder success = new StringBuilder();
        StringBuilder failed = new StringBuilder();

        // From Static to Custom
        if (!StringUtils.isBlank(staticToCustom)) {
            List<String> targets = Constants.ALL.equals(staticToCustom)
                    ? getStaticHyperlanePositions(game)
                    : Helper.getListFromCSV(staticToCustom);

            for (String position : targets) {
                Tile tile = game.getTileByPosition(position);
                if (isStaticHyperlane(tile)) {
                    String data = Mapper.getHyperlaneData(tile.getTileID());
                    if (!StringUtils.isBlank(data)) {
                        AddTileService.addTile(game, new Tile(HYPERLANE_TILEID, position));
                        game.getCustomHyperlaneData().put(position, data);
                        success.append(position).append(' ');
                    } else {
                        failed.append(position).append(' ');
                    }
                } else {
                    failed.append(position).append(' ');
                }
            }
        }

        // From Custom to Static
        if (!StringUtils.isBlank(customToStatic)) {
            List<String> targets = Constants.ALL.equals(customToStatic)
                    ? new ArrayList<>(game.getCustomHyperlaneData().keySet())
                    : Helper.getListFromCSV(customToStatic);

            for (String position : targets) {
                String customData = game.getCustomHyperlaneData().get(position);
                String staticHyperlaneTileId = customData != null ? Mapper.getHyperlaneTileId(customData) : null;
                if (staticHyperlaneTileId != null) {
                    game.getCustomHyperlaneData().remove(position);
                    AddTileService.addTile(game, new Tile(staticHyperlaneTileId, position));
                    success.append(position).append(' ');
                } else {
                    failed.append(position).append(' ');
                }
            }
        }

        StringBuilder message = new StringBuilder("Transformed: " + success + "\nCould not transform: " + failed);
        if (!failed.isEmpty() && hasOneWayCustomHyperlane(game, failed.toString())) {
            message.append("\n-# Hyperlanes with one-way lanes have no static tile and stay custom.");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message.toString());
    }

    private static boolean hasOneWayCustomHyperlane(Game game, String positions) {
        return Arrays.stream(positions.trim().split(" "))
                .anyMatch(
                        position -> hasOneWayLanes(game.getCustomHyperlaneData().get(position)));
    }

    private static List<String> getStaticHyperlanePositions(Game game) {
        return game.getTileMap().values().stream()
                .filter(CustomHyperlaneService::isStaticHyperlane)
                .map(Tile::getPosition)
                .collect(Collectors.toList());
    }

    private boolean isStaticHyperlane(Tile tile) {
        return tile != null
                && tile.getTileModel() != null
                && tile.getTileModel().isHyperlane()
                && !isCustomHyperlaneTile(tile);
    }

    /** Rotates a 6x6 hyperlane connection matrix string by 60 degrees: [i][j] -> [(i+1)%6][(j+1)%6]. */
    public static String rotateMatrix60(String matrix) {
        if (matrix == null) return null;
        int[][] mat = parseMatrix(matrix);
        int[][] rotated = new int[6][6];
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                rotated[(i + 1) % 6][(j + 1) % 6] = mat[i][j];
            }
        }
        return formatMatrix(rotated);
    }

    public static String encodeMatrix(String matrix) {
        StringBuilder binaryBuilder = new StringBuilder(36);
        String[] rows = matrix.split(";");
        for (String row : rows) {
            for (String val : row.split(",")) {
                binaryBuilder.append(val.trim());
            }
        }

        BigInteger bigInt = new BigInteger(binaryBuilder.toString(), 2);
        return String.format("%09x", bigInt); // 9 hex chars = 36 bits
    }

    public static String decodeMatrix(String hex) {
        String binaryString = hex; // to support old binary import
        if (hex.length() == 9) {
            BigInteger bigInt = new BigInteger(hex, 16);
            binaryString = String.format("%36s", bigInt.toString(2)).replace(' ', '0'); // pad to 36 bits
        }

        StringBuilder matrixBuilder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) matrixBuilder.append(";");
            for (int j = 0; j < 6; j++) {
                if (j > 0) matrixBuilder.append(",");
                matrixBuilder.append(binaryString.charAt(i * 6 + j));
            }
        }
        return matrixBuilder.toString();
    }

    public static boolean isValidConnectionMatrix(String input) {
        String[] rows = input.split(";");
        if (rows.length != 6) return false;

        for (String row : rows) {
            String[] cells = row.split(",");
            if (cells.length != 6) return false;

            for (String cell : cells) {
                String trimmed = cell.trim();
                if (!"0".equals(trimmed) && !"1".equals(trimmed) && !ONE_WAY.equals(trimmed)) return false;
            }
        }

        return true;
    }

    public static boolean isValidStoredMatrix(String input) {
        return isValidConnectionMatrix(input) && !input.contains(ONE_WAY);
    }

    public static String toStoredMatrix(String typed) {
        int[][] grid = parseMatrix(typed);
        int[][] stored = new int[6][6];
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (grid[i][j] == 1) {
                    stored[i][j] = 1;
                    stored[j][i] = 1;
                } else if (grid[i][j] == 2) {
                    stored[i][j] = 1;
                }
            }
        }
        connectRoundabout(stored);
        return formatMatrix(stored);
    }

    private static void connectRoundabout(int[][] grid) {
        boolean[] entry = new boolean[6], exit = new boolean[6];
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == j || grid[i][j] == 0 || grid[i][i] == 0 || grid[j][j] == 0) continue;
                entry[i] = true;
                exit[j] = true;
            }
        }
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (i != j && entry[i] && exit[j]) grid[i][j] = 1;
            }
        }
    }

    public static String toTypedMatrix(String stored) {
        int[][] grid = parseMatrix(stored);
        int[][] typed = new int[6][6];
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (grid[i][j] == 1) {
                    typed[i][j] = grid[j][i] == 1 ? 1 : 2;
                }
            }
        }
        return formatMatrix(typed);
    }

    public static String onlyLanes(String stored, String position, Set<String> lanes) {
        int[][] grid = parseMatrix(stored);
        int[][] kept = new int[6][6];
        for (int i = 0; i < 6; i++) {
            for (int j = i; j < 6; j++) {
                if (!lanes.contains(GridRule.lane(position, i, j))) continue;
                kept[i][j] = grid[i][j];
                kept[j][i] = grid[j][i];
                kept[i][i] = grid[i][i];
                kept[j][j] = grid[j][j];
            }
        }
        return formatMatrix(kept);
    }

    public static boolean hasOneWayLanes(String stored) {
        return stored != null && toTypedMatrix(stored).contains(ONE_WAY);
    }

    public static Set<Integer> oneWayExitSides(String stored) {
        int[][] grid = parseMatrix(stored);
        boolean roundabout = hasSelfConnection(stored);
        Set<Integer> exits = new TreeSet<>();
        for (int side = 0; side < 6; side++) {
            if (roundabout ? isExitOnlyRoundaboutSide(grid, side) : hasOneWayLaneInto(grid, side)) exits.add(side);
        }
        return exits;
    }

    private static boolean hasOneWayLaneInto(int[][] grid, int side) {
        for (int from = 0; from < 6; from++) {
            if (from != side && grid[from][side] == 1 && grid[side][from] == 0) return true;
        }
        return false;
    }

    private static boolean isExitOnlyRoundaboutSide(int[][] grid, int side) {
        boolean entered = false, left = false;
        for (int other = 0; other < 6; other++) {
            if (other == side) continue;
            entered |= grid[other][side] == 1;
            left |= grid[side][other] == 1;
        }
        return grid[side][side] == 1 && entered && !left;
    }

    public static List<String> describeOneWayLanes(String stored) {
        int[][] grid = parseMatrix(stored);
        List<String> lanes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (i != j && grid[i][j] == 1 && grid[j][i] == 0) {
                    lanes.add(SIDE_NAMES.get(i) + " → " + SIDE_NAMES.get(j));
                }
            }
        }
        return lanes;
    }

    public static int[][] parseMatrix(String matrix) {
        String[] rows = matrix.split(";");
        int[][] grid = new int[6][6];
        for (int i = 0; i < 6; i++) {
            String[] cols = rows[i].split(",");
            for (int j = 0; j < 6; j++) {
                grid[i][j] = Integer.parseInt(cols[j].trim());
            }
        }
        return grid;
    }

    public static String formatMatrix(int[][] grid) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) builder.append(";");
            for (int j = 0; j < 6; j++) {
                if (j > 0) builder.append(",");
                builder.append(grid[i][j]);
            }
        }
        return builder.toString();
    }

    // If any direction connects to itself, we'll generate the tile as roundabout
    public static boolean hasSelfConnection(String matrix) {
        if (matrix == null) return false;

        String[] rows = matrix.split(";");
        if (rows.length != 6) return false;

        for (int i = 0; i < 6; i++) {
            String[] cols = rows[i].split(",");
            if (cols.length != 6) continue;

            if ("1".equals(cols[i].trim())) {
                return true;
            }
        }
        return false;
    }

    public static String getHyperlaneDataForTile(Tile tile, Game game) {
        if (isCustomHyperlaneTile(tile)) {
            return game.getCustomHyperlaneData().get(tile.getPosition());
        }
        return null;
    }

    public static void moveCustomHyperlaneData(String from, String to, Game game) {
        moveCustomHyperlaneData(from, to, game, false);
    }

    public static void moveCustomHyperlaneData(String from, String to, Game game, boolean twoWays) {
        Map<String, String> data = game.getCustomHyperlaneData();
        String dataFrom = data.remove(from);
        String dataTo = data.remove(to);

        if (dataFrom != null) data.put(to, dataFrom);
        if (twoWays && dataTo != null) data.put(from, dataTo);
    }
}
