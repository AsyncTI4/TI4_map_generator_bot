package ti4.service.fow;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.SortHelper;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.TileModel.TileBack;

public class LoreService {
                  
    private static final List<Button> SYSTEM_LORE_BUTTONS = Arrays.asList(
        Buttons.blue("gmSystemLoreEdit~MDL", "Add New"),
        Buttons.gray("gmSystemLoreRefresh", "Refresh"),
        Buttons.DONE_DELETE_BUTTONS
    );

    private static final String SYSTEM_LORE_KEY = "fowSystemLore";

    
    @ButtonHandler("gmSystemLoreRefresh")
    private static void refreshSystemLoreButtons(ButtonInteractionEvent event, String buttonID, Game game) {
        showSystemLoreButtons(event, buttonID, game);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("gmSystemLore")
    private static void showSystemLoreButtons(ButtonInteractionEvent event, String buttonID, Game game) {
        String page = StringUtils.substringAfter(buttonID, "page");
        int pageNum = StringUtils.isBlank(page) ? 1 : Integer.parseInt(page);
        List<ActionRow> buttons = Buttons.paginateButtons(getSystemLoreButtons(game), SYSTEM_LORE_BUTTONS, pageNum, "gmSystemLore");
        
        if (StringUtils.isBlank(page)) {
            String msg = "### System Lore\n-# Shown to the first player to conclude an action with units in the system.";
            GMService.getGMChannel(game).sendMessage(msg).setComponents(buttons).queue();
        } else {
            event.getHook().editOriginalComponents(buttons).queue();
        }
    }

    private static List<Button> getSystemLoreButtons(Game game) {
        List<Button> systemLoreButtons = new ArrayList<>();
        for (Map.Entry<String, String> lore : getSavedLore(game).entrySet()) {
            String position = lore.getKey();
            Tile tile = game.getTileByPosition(position);
            systemLoreButtons.add(Buttons.green("gmSystemLoreEdit_" + position + "~MDL", 
                position + " " + (tile == null ? "null" : tile.getRepresentation())));
        }
        SortHelper.sortButtonsByTitle(systemLoreButtons);
        return systemLoreButtons;
    }

    private static Map<String, String> getSavedLore(Game game) {
        Map<String, String> savedLoreMap = new HashMap<>();
        String savedLoreString = game.getStoredValue(SYSTEM_LORE_KEY);
        if (StringUtils.isNotBlank(savedLoreString)) {
            for (String savedLore : savedLoreString.split("\\|")) {
                String[] splitLore = savedLore.split(";");
                if (splitLore.length == 2) {
                    savedLoreMap.put(splitLore[0], splitLore[1]);
                } else {
                    BotLogger.warning(new BotLogger.LogMessageOrigin(game), "Invalid lore string: " + savedLore);
                }
            }
        }
        return savedLoreMap;
    }

    @ButtonHandler("gmSystemLoreEdit")
    public static void editSystemLore(ButtonInteractionEvent event, String buttonID, Game game) {
        String existingPosition = buttonID.contains("_") ? StringUtils.substringBetween(buttonID, "gmSystemLoreEdit_", "~MDL") : "";

        TextInput.Builder position = TextInput.create(Constants.POSITION, "Position", TextInputStyle.SHORT)
            .setRequired(true)
            .setPlaceholder("000")
            .setMaxLength(4);
        TextInput.Builder lore = TextInput.create(Constants.MESSAGE, "Lore (clear to delete)", TextInputStyle.PARAGRAPH)
            .setRequired(false)
            .setPlaceholder("There once was Mecatol...")
            .setMaxLength(1000);

        if (StringUtils.isNotBlank(existingPosition)) {
            position.setValue(existingPosition);
            lore.setValue(getSavedLore(game).get(existingPosition));
        }

        Modal editLoreModal = Modal.create("gmSystemLoreSave", "Add Lore to Position")
            .addActionRow(position.build())
            .addActionRow(lore.build())
            .build();

        event.replyModal(editLoreModal).queue();
    }

    @ModalHandler("gmSystemLoreSave")
    public static void saveSystemLore(ModalInteractionEvent event, Player player, Game game) {
        String position = event.getValue(Constants.POSITION).getAsString();
        String loreText = event.getValue(Constants.MESSAGE).getAsString();

        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Position " + position + " is invalid to save lore `" + loreText + "`");
            return;
        }

        Map<String, String> savedLoreMap = getSavedLore(game);
        if (StringUtils.isBlank(loreText)) {
            savedLoreMap.remove(position);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Removed Lore from " + position);
        } else {
            savedLoreMap.put(position, loreText.replace(";", "").replace("|", ""));
            MessageHelper.sendMessageToChannel(event.getChannel(), "Saved Lore to " + position);
        }

        setSystemLore(game, savedLoreMap);
    }

    private static void setSystemLore(Game game, Map<String, String> systemLore) {
        String loreString = systemLore.entrySet().stream()
            .map(entry -> entry.getKey() + ";" + entry.getValue())
            .collect(Collectors.joining("|"));
        game.setStoredValue(SYSTEM_LORE_KEY, loreString);
    }

    private static MessageEmbed buildLoreEmbed(Game game, String position, String lore) {
        Tile tile = game.getTileByPosition(position);
        String titleTile = position;
        Color embedColor = Color.black;
        if (tile != null && tile.getTileModel() != null) {
            titleTile += " - " + tile.getTileModel().getNameNullSafe() + " " + tile.getTileModel().getEmoji();
            switch (tile.getTileModel().getTileBack()) {
                case TileBack.RED -> embedColor = Color.red;
                case TileBack.BLUE -> embedColor = Color.blue;
                case TileBack.GREEN -> embedColor = Color.green;
                default -> embedColor = Color.black;
            }
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("⭐ Lore of " + titleTile);
        eb.setDescription(lore);
        eb.setColor(embedColor);
        return eb.build();
    }

    public static void showSystemLore(Player player, Game game) {
        String pos = game.getActiveSystem();
        if (!FoWHelper.playerHasUnitsInSystem(player, game.getTileByPosition(pos))) {
            return;
        }

        Map<String, String> systemLore = getSavedLore(game);
        if (systemLore.isEmpty() || !systemLore.containsKey(pos)) {
            return;
        }

        MessageEmbed embed = buildLoreEmbed(game, pos, systemLore.get(pos));
        MessageHelper.sendMessageToChannelWithEmbed(player.getPrivateChannel(), "You found a Lore Fragment", embed);
        
        GMService.logPlayerActivity(game, player, player.getRepresentationUnfoggedNoPing() + " was shown the lore of " + pos);

        systemLore.remove(pos);
        setSystemLore(game, systemLore);
    }
}
