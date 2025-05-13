package ti4.service.map;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.helpers.SortHelper;
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

    public static boolean isCustomHyperlaneTile(Tile tile) {
        return HYPERLANE_TILEID.equals(tile.getTileID());
    }

    public static void offerManageHyperlaneButtons(Game game, GenericInteractionCreateEvent event) {
        offerManageHyperlaneButtons(game, event, null);
    }

    //Red button    has hyperlane data on invalid tile
    //Green button  has existing hyperlane data
    //Blue button   empty hyperlane tile without data
    private static void offerManageHyperlaneButtons(Game game, GenericInteractionCreateEvent event, String originalMessageId) {
        StringBuffer sb = new StringBuffer("### Manage Custom Hyperlanes\n");

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
       
        if (hyperlaneTileButtons.isEmpty()) {
            sb.append("No HL tiles found. Use `/map add_tile tile_name:").append(HYPERLANE_TILEID).append("` to add.");
        } else {
            SortHelper.sortButtonsByTitle(hyperlaneTileButtons);
            hyperlaneTileButtons.add(Buttons.gray("customHyperlaneRefresh", "Refresh"));
            hyperlaneTileButtons.add(Buttons.gray("customHyperlaneImport~MDL", "Import"));
            hyperlaneTileButtons.add(Buttons.gray("customHyperlaneExport", "Export"));
            hyperlaneTileButtons.add(Buttons.DONE_DELETE_BUTTONS);
        }

        if (originalMessageId == null) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), sb.toString(), hyperlaneTileButtons);
        } else {
            List<List<ActionRow>> buttonRows = MessageHelper.getPartitionedButtonLists(hyperlaneTileButtons);
            event.getMessageChannel().editMessageById(originalMessageId, sb.toString()).setComponents(buttonRows.getFirst()).queue();
        }
    }
  
    @ButtonHandler("customHyperlaneRefresh")
    public static void refreshHyperlaneButtons(ButtonInteractionEvent event, Game game) {
        offerManageHyperlaneButtons(game, event, null);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("customHyperlaneExport")
    public static void exportHyperlaneData(ButtonInteractionEvent event, Game game) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : game.getCustomHyperlaneData().entrySet()) {
            sb.append(entry.getKey()).append(",").append(matrixToBinary(entry.getValue())).append("\n");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }

    @ButtonHandler("customHyperlaneImport~MDL")
    public static void importHyperlaneData(ButtonInteractionEvent event, Game game) {
        TextInput.Builder data = TextInput.create(Constants.SETTING_VALUE, "Hyperlane Data", TextInputStyle.PARAGRAPH)
            .setPlaceholder("position,data as binary string\nposition,data as binary string");

        Modal importDataModal = Modal.create("customHyperlaneImportSave_" + event.getMessageId(), "Import Data (overwrites existing)")
            .addActionRow(data.build())
            .build();

        event.replyModal(importDataModal).queue();
    }

    @ModalHandler("customHyperlaneImportSave_")
    public static void saveImportedHyperlaneData(ModalInteractionEvent event, Player player, Game game) {
        String origMessageId = event.getModalId().replace("customHyperlaneImportSave_", "");
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
            
            String matrix = binaryToMatrix(data[1].trim());
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

        offerManageHyperlaneButtons(game, event, origMessageId);
    }

    @ButtonHandler("customHyperlaneEdit")
    public static void editHyperlaneData(ButtonInteractionEvent event, String buttonID, Game game) {
        String position = StringUtils.substringBetween(buttonID, "customHyperlaneEdit_", "~MDL");

        TextInput.Builder data = TextInput.create(Constants.SETTING_VALUE, "Hyperlane Matrix (empty to remove)", TextInputStyle.PARAGRAPH)
            .setPlaceholder("0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0")
            .setValue("0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0;\n0,0,0,0,0,0")
            .setRequired(false)
            .setMaxLength(76);

        Map<String, String> customHyperlaneData = game.getCustomHyperlaneData();
        if (customHyperlaneData.containsKey(position)) {
            data.setValue(customHyperlaneData.get(position).replace(";", ";\n"));
        }

        Modal customHyperlaneModal = Modal.create("customHyperlaneSave_" + position + "_" + event.getMessageId(), position + " Hyperlane")
            .addActionRow(data.build())
            .build();

        event.replyModal(customHyperlaneModal).queue();
    }

    @ModalHandler("customHyperlaneSave_")
    public static void saveHyperlaneData(ModalInteractionEvent event, Player player, Game game) {
        String[] modalId = event.getModalId().split("_");
        String position = modalId[1];
        String origMessageId = modalId[2];
        String hyperlaneData = event.getValue(Constants.SETTING_VALUE).getAsString().replace("\n", "");

        Map<String, String> customHyperlaneData = game.getCustomHyperlaneData();
        if (StringUtils.isBlank(hyperlaneData)) {
            customHyperlaneData.remove(position);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Hyperlane data removed from " + position);
        } else {
            if (!isValidConnectionMatrix(hyperlaneData)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Data `" + hyperlaneData + "` is invalid.");
                return;
            }

            hyperlaneData = normalizeMatrix(hyperlaneData); //force two-way connections
            customHyperlaneData.put(position, hyperlaneData);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Hyperlane data `" + hyperlaneData + "` added to " + position);
        }

        offerManageHyperlaneButtons(game, event, origMessageId);
    }

    public static String matrixToBinary(String matrix) {
        StringBuilder binaryBuilder = new StringBuilder(36);
        String[] rows = matrix.split(";");
        for (String row : rows) {
            for (String val : row.split(",")) {
                binaryBuilder.append(val.trim());
            }
        }
        return binaryBuilder.toString();
    }

    public static String encodeMatrix(String matrix) {
        String binaryString = matrixToBinary(matrix);
        BigInteger bigInt = new BigInteger(binaryString, 2);
        return String.format("%09x", bigInt); // 9 hex chars = 36 bits
    }

    public static String binaryToMatrix(String binaryString) {
        if (binaryString == null || binaryString.length() != 36) {
            return "";
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

    public static String decodeMatrix(String hex) {
        BigInteger bigInt = new BigInteger(hex, 16);
        String binaryString = String.format("%36s", bigInt.toString(2)).replace(' ', '0'); // pad to 36 bits
        return binaryToMatrix(binaryString);
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
}
