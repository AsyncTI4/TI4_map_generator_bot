package ti4.service.map;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.SortHelper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

@UtilityClass
public class CustomHyperlaneService {
    private static final String HYPERLANE_TILEID = "hl";

    private static final List<Button> HYPERLANE_BUTTONS = Arrays.asList(
        Buttons.gray("customHyperlaneRefresh", "Refresh"),
        Buttons.gray("customHyperlaneMore", "More"),
        Buttons.DONE_DELETE_BUTTONS
    );

    private static final List<Button> HYPERLANE_MORE_BUTTONS = Arrays.asList(
        Buttons.gray("customHyperlaneImport~MDL", "Import", "⬇️"),
        Buttons.gray("customHyperlaneExport", "Export", "⬆️"),
        Buttons.gray("customHyperlaneTransform~MDL", "Transform", "↔️"),
        Buttons.DONE_DELETE_BUTTONS
    );

    public static boolean isCustomHyperlaneTile(Tile tile) {
        return HYPERLANE_TILEID.equals(tile.getTileID());
    }

    public static void offerManageHyperlaneButtons(Game game, GenericInteractionCreateEvent event) {
        offerManageHyperlaneButtons(game, event, null);
    }

    @ButtonHandler("customHyperlanePagination")
    public static void offerManageHyperlaneButtons(ButtonInteractionEvent event, String buttonID, Game game) {
        offerManageHyperlaneButtons(game, event, buttonID);
    }

    //Red button    has hyperlane data on invalid tile
    //Green button  has existing hyperlane data
    //Blue button   empty hyperlane tile without data
    private static void offerManageHyperlaneButtons(Game game, GenericInteractionCreateEvent event, String buttonID) {
        String page = StringUtils.substringAfter(buttonID, "page");
        int pageNum = StringUtils.isBlank(page) ? 1 : Integer.parseInt(page);
        List<Button> hyperlaneTileButtons = getHyperlaneButtons(game);
        List<ActionRow> buttons = Buttons.paginateButtons(hyperlaneTileButtons, HYPERLANE_BUTTONS, pageNum, "customHyperlanePagination");  

        if (StringUtils.isBlank(page)) {
            StringBuffer sb = new StringBuffer("### Manage Custom Hyperlanes");
            if (hyperlaneTileButtons.isEmpty()) {
                sb.append("\nNo hyperlane tiles found. Use `/map add_tile tile_name:").append(HYPERLANE_TILEID).append("` to add.");
            }
            event.getMessageChannel().sendMessage(sb.toString()).setComponents(buttons).queue();
        } else {
            ((ButtonInteractionEvent)event).getHook().editOriginalComponents(buttons).queue();
        }
    }

    private static List<Button> getHyperlaneButtons(Game game) {
        Map<String, String> customHyperlaneData = game.getCustomHyperlaneData();
        List<Button> hyperlaneTileButtons = new ArrayList<>();
        for (String position : customHyperlaneData.keySet()) {
            Tile tileWithExistingData = game.getTileByPosition(position);
            hyperlaneTileButtons.add(tileWithExistingData == null || !isCustomHyperlaneTile(tileWithExistingData)
                ? Buttons.red("customHyperlaneEdit_" + position + "~MDL", position)
                : Buttons.green("customHyperlaneEdit_" + position + "~MDL", position));
        }
        for (Entry<String, Tile> entry : game.getTileMap().entrySet()) {
            String position = entry.getKey();
            if (isCustomHyperlaneTile(entry.getValue()) && !game.getCustomHyperlaneData().containsKey(position)) {
                hyperlaneTileButtons.add(Buttons.blue("customHyperlaneEdit_" + position + "~MDL", position));
            }
        }
        SortHelper.sortButtonsByTitle(hyperlaneTileButtons);
        return hyperlaneTileButtons;
    }
  
    @ButtonHandler("customHyperlaneRefresh")
    public static void refreshHyperlaneButtons(ButtonInteractionEvent event, Game game) {
        offerManageHyperlaneButtons(game, event, null);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("customHyperlaneMore")
    public static void moreHyperlaneButtons(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "", HYPERLANE_MORE_BUTTONS);
    }

    @ButtonHandler("customHyperlaneExport")
    public static void exportHyperlaneData(ButtonInteractionEvent event, Game game) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : game.getCustomHyperlaneData().entrySet()) {
            sb.append(entry.getKey()).append(",").append(encodeMatrix(entry.getValue())).append(" ");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }

    @ButtonHandler("customHyperlaneImport~MDL")
    public static void importHyperlaneData(ButtonInteractionEvent event, Game game) {
        TextInput.Builder data = TextInput.create(Constants.SETTING_VALUE, "Hyperlane Data", TextInputStyle.PARAGRAPH);

        Modal importDataModal = Modal.create("customHyperlaneImportSave", "Import Data (overwrites existing)")
            .addActionRow(data.build())
            .build();

        event.replyModal(importDataModal).queue();
    }

    @ModalHandler("customHyperlaneImportSave")
    public static void saveImportedHyperlaneData(ModalInteractionEvent event, Player player, Game game) {
        String importData = event.getValue(Constants.SETTING_VALUE).getAsString().replace("\n", " ");

        Map<String, String> customHyperlaneData = new HashMap<>();
        for (String dataRow : importData.split(" ")) {
            String[] data = dataRow.split(",");
            if (data.length != 2) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid data: `" + dataRow + "`");
                return;
            }

            String position = data[0];
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid position: `" + position + "`");
                return;
            }
            
            String matrix = decodeMatrix(data[1].trim());
            if (!isValidConnectionMatrix(matrix)) {
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

        TextInput.Builder data = TextInput.create(Constants.SETTING_VALUE, "Hyperlane Matrix (clear to delete)", TextInputStyle.PARAGRAPH)
            .setPlaceholder("0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0")
            .setValue("0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0")
            .setRequired(false)
            .setMaxLength(76);

        Map<String, String> customHyperlaneData = game.getCustomHyperlaneData();
        if (customHyperlaneData.containsKey(position)) {
            data.setValue(customHyperlaneData.get(position).replace(";", ";\n"));
        }

        Modal customHyperlaneModal = Modal.create("customHyperlaneSave_" + position, position + " Hyperlane")
            .addActionRow(data.build())
            .build();

        event.replyModal(customHyperlaneModal).queue();
    }

    @ModalHandler("customHyperlaneSave_")
    public static void saveHyperlaneData(ModalInteractionEvent event, Player player, Game game) {
        String[] modalId = event.getModalId().split("_");
        String position = modalId[1];
        String hyperlaneData = event.getValue(Constants.SETTING_VALUE).getAsString().replace("\n", "");

        Map<String, String> customHyperlaneData = game.getCustomHyperlaneData();
        if (StringUtils.isBlank(hyperlaneData)) {
            customHyperlaneData.remove(position);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Hyperlane data removed from " + position + ".");
        } else {
            if (!isValidConnectionMatrix(hyperlaneData)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Data `" + hyperlaneData + "` is invalid.");
                return;
            }

            hyperlaneData = normalizeMatrix(hyperlaneData); //force two-way connections
            customHyperlaneData.put(position, hyperlaneData);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Hyperlane data `" + hyperlaneData + "` added to " + position + ".");
        }
    }

    @ButtonHandler("customHyperlaneTransform~MDL")
    public static void transformHyperlane(ButtonInteractionEvent event, Game game) {
        TextInput.Builder data1 = TextInput.create("staticToCustom", "Static -> Custom", TextInputStyle.SHORT)
            .setPlaceholder("Comma separated positions or ALL")
            .setRequired(false);
        TextInput.Builder data2 = TextInput.create("customToStatic", "Custom -> Static", TextInputStyle.SHORT)
            .setPlaceholder("Comma separated positions or ALL")
            .setRequired(false);

        Modal modal = Modal.create("customHyperlaneTransformExecute", "Transform Hyperlanes")
            .addActionRow(data1.build())
            .addActionRow(data2.build())
            .build();

        event.replyModal(modal).queue();
    }

    @ModalHandler("customHyperlaneTransformExecute")
    public static void transformHyperlaneExecute(ModalInteractionEvent event, Player player, Game game) {
        String staticToCustom = event.getValue("staticToCustom").getAsString();
        String customToStatic = event.getValue("customToStatic").getAsString();

        StringBuilder success = new StringBuilder();
        StringBuilder failed = new StringBuilder();

        //From Static to Custom
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
                        success.append(position).append(" ");
                    } else {
                        failed.append(position).append(" ");
                    }
                } else {
                    failed.append(position).append(" ");
                }
            }
        } 

        //From Custom to Static
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
                    success.append(position).append(" ");
                } else {
                    failed.append(position).append(" ");
                }
            }
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), 
            "Transformed: " + success + "\nCould not transform: " + failed);
    }

    private static List<String> getStaticHyperlanePositions(Game game) {
        return game.getTileMap().values().stream()
            .filter(tile -> isStaticHyperlane(tile))
            .map(Tile::getPosition)
            .collect(Collectors.toList());
    }

    private boolean isStaticHyperlane(Tile tile) {
        return tile != null 
            && tile.getTileModel() != null 
            && tile.getTileModel().isHyperlane() 
            && !isCustomHyperlaneTile(tile);
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
        String binaryString = hex; //to support old binary import
        if (hex.length() == 9) {
            BigInteger bigInt = new BigInteger(hex, 16);
            binaryString = String.format("%36s", 
                bigInt.toString(2)).replace(' ', '0'); // pad to 36 bits
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
                if (!trimmed.equals("0") && !trimmed.equals("1")) return false;
            }
        }
    
        return true;
    }

    //Ensure connections are always marked both ways
    public static String normalizeMatrix(String matrix) {
        String[] rows = matrix.split(";");
        int[][] grid = new int[6][6];
    
        for (int i = 0; i < 6; i++) {
            String[] cols = rows[i].split(",");
            for (int j = 0; j < 6; j++) {
                grid[i][j] = Integer.parseInt(cols[j].trim());
            }
        }
    
        // Make symmetric: if grid[i][j] == 1, then grid[j][i] = 1
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (grid[i][j] == 1 || grid[j][i] == 1) {
                    grid[i][j] = 1;
                    grid[j][i] = 1;
                }
            }
        }
    
        // Rebuild string format
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

    //If any direction connects to itself, we'll generate the tile as roundabout
    public static boolean hasSelfConnection(String matrix) {
        if (matrix == null) return false;

        String[] rows = matrix.split(";");
        if (rows.length != 6) return false;
    
        for (int i = 0; i < 6; i++) {
            String[] cols = rows[i].split(",");
            if (cols.length != 6) continue;
    
            if (cols[i].trim().equals("1")) {
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
