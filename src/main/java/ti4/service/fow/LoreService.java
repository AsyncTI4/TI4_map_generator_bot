package ti4.service.fow;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.SortHelper;
import ti4.helpers.URLReaderHelper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;

public class LoreService {

    private static final List<Button> LORE_BUTTONS = Arrays.asList(
            Buttons.blue("gmLoreEdit_NEW~MDL", "Add Lore"),
            Buttons.gray("gmLoreExportImportButtons", "Export/Import"),
            Buttons.gray("gmLoreRefresh", "Refresh"));

    private static final List<Button> LORE_EXPORT_IMPORT_BUTTONS = Arrays.asList(
            Buttons.gray("gmLoreExport", "Export"),
            Buttons.gray("gmLoreImport~MDL", "Import"),
            Buttons.DONE_DELETE_BUTTONS);

    private static final String SYSTEM_LORE_KEY = "fowSystemLore";
    private static final String LORE_EXPORT_FILENAME = "_lore_export.txt";
    private static final int LORE_TEXT_MAX_LENGTH = 1000;
    private static final int FOOTER_TEXT_MAX_LENGTH = 200;

    @ButtonHandler("gmLoreRefresh")
    private static void refreshLoreButtons(ButtonInteractionEvent event, String buttonID, Game game) {
        showLoreButtons(event, buttonID, game);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("gmLoreExportImportButtons")
    private static void gmLoreExportImportButtons(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "", LORE_EXPORT_IMPORT_BUTTONS);
    }

    @ButtonHandler("gmLoreExport")
    private static void exportLore(ButtonInteractionEvent event, Game game) {
        File exportFile = new File(game.getName() + LORE_EXPORT_FILENAME);
        try (FileWriter writer = new FileWriter(exportFile, StandardCharsets.UTF_8)) {
            writer.write(game.getStoredValue(SYSTEM_LORE_KEY));
        } catch (IOException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Failed to export lore: " + e.getMessage());
            return;
        }

        event.getChannel()
                .sendFiles(FileUpload.fromData(exportFile))
                .setContent("### Export of " + game.getName() + " Lore:")
                .queue();
    }

    @ButtonHandler("gmLoreImport~MDL")
    public static void importLore(ButtonInteractionEvent event, Game game) {
        TextInput.Builder url = TextInput.create("url", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("http://your.url/fow123_lore_export.txt");

        Modal importLoreModal = Modal.create("gmLoreImportFromURL", "Import lore from URL")
                .addComponents(Label.of("URL", url.build()))
                .build();
        event.replyModal(importLoreModal).queue();
    }

    @ModalHandler("gmLoreImportFromURL")
    public static void importLoreFromURL(ModalInteractionEvent event, Game game) {
        String url = event.getValue("url").getAsString();
        String loreString = URLReaderHelper.readFromURL(url, event.getChannel());
        if (loreString == null) {
            return;
        }

        Map<String, String[]> importedLore = readLore(loreString);
        // Validate
        for (Map.Entry<String, String[]> entry : importedLore.entrySet()) {
            String validatedTarget =
                    validateLore(entry.getKey(), entry.getValue()[0], entry.getValue()[1], importedLore, game);
            if (validatedTarget == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), entry.getKey() + " is invalid to import lore");
                return;
            }
        }

        // Construct
        saveLore(game, importedLore);
        MessageHelper.sendMessageToChannel(event.getChannel(), importedLore.size() + " lore entries imported.");
    }

    @ButtonHandler("gmLore")
    private static void showLoreButtons(ButtonInteractionEvent event, String buttonID, Game game) {
        String page = StringUtils.substringAfter(buttonID, "page");
        int pageNum = StringUtils.isBlank(page) ? 1 : Integer.parseInt(page);
        List<ActionRow> buttons = Buttons.paginateButtons(getLoreButtons(game), LORE_BUTTONS, pageNum, "gmLore");

        if (StringUtils.isBlank(page)) {
            String msg = """
                ### Lore Management\

                -# System Lore is shown to the first player to conclude an action with units in the system.\

                -# Planet Lore is shown to the first player to gain control of the planet.""";
            event.getChannel().sendMessage(msg).setComponents(buttons).queue();
        } else {
            event.getHook().editOriginalComponents(buttons).queue();
        }
    }

    private static List<Button> getLoreButtons(Game game) {
        List<Button> loreButtons = new ArrayList<>();
        for (String target : getSavedLore(game).keySet()) {
            String buttonLabel;
            String emoji;
            boolean isValidLore = true;

            if (PositionMapper.isTilePositionValid(target)) {
                // System Lore
                Tile tile = game.getTileByPosition(target);
                if (tile == null) isValidLore = false;

                buttonLabel = target;
                emoji = tile != null ? tile.getTileModel().getEmoji().toString() : null;
            } else {
                // Planet Lore
                PlanetModel planet = Mapper.getPlanet(target);
                if (!game.getPlanets().contains(target)) isValidLore = false;

                buttonLabel = planet == null ? target : planet.getName();
                emoji = planet != null ? planet.getEmoji().toString() : null;
            }

            if (isValidLore) {
                loreButtons.add(Buttons.green("gmLoreEdit_" + target + "~MDL", buttonLabel, emoji));
            } else {
                loreButtons.add(Buttons.red("gmLoreEdit_" + target + "~MDL", buttonLabel, emoji));
            }
        }
        SortHelper.sortButtonsByTitle(loreButtons);
        return loreButtons;
    }

    public static Map<String, String[]> getSavedLore(Game game) {
        return readLore(game.getStoredValue(SYSTEM_LORE_KEY));
    }

    private static Map<String, String[]> readLore(String loreString) {
        Map<String, String[]> savedLoreMap = new HashMap<>();
        if (StringUtils.isNotBlank(loreString)) {
            for (String savedLore : loreString.split("\\|")) {
                String[] splitLore = savedLore.split(";");
                if (splitLore.length == 2 || splitLore.length == 3) {
                    savedLoreMap.put(
                            splitLore[0].trim(),
                            new String[] {clean(splitLore[1]), splitLore.length == 3 ? clean(splitLore[2]) : ""});
                }
            }
        }
        return savedLoreMap;
    }

    @ButtonHandler("gmLoreEdit")
    public static void editLore(ButtonInteractionEvent event, String buttonID, Game game) {
        String target = StringUtils.substringBetween(buttonID, "gmLoreEdit_", "~MDL");

        TextInput.Builder position = TextInput.create(Constants.POSITION, TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("000, Sem-Lore");
        TextInput.Builder lore = TextInput.create(Constants.MESSAGE, TextInputStyle.PARAGRAPH)
                .setRequired(false)
                .setPlaceholder("Once upon a time...")
                .setMaxLength(LORE_TEXT_MAX_LENGTH);
        TextInput.Builder footer = TextInput.create("footer", TextInputStyle.SHORT)
                .setRequired(false)
                .setPlaceholder("Please use `/add_token token:gravityrift` on this system.")
                .setMaxLength(FOOTER_TEXT_MAX_LENGTH);

        if (!"NEW".equals(target)) {
            position.setValue(target);
            String[] savedLore = getSavedLore(game).get(target);
            lore.setValue(savedLore[0]);
            if (StringUtils.isNotBlank(savedLore[1])) {
                footer.setValue(savedLore[1]);
            }
        }

        Modal editLoreModal = Modal.create("gmLoreSave", "Add Lore")
                .addComponents(
                        Label.of("Target", position.build()),
                        Label.of("Lore (clear to delete)", lore.build()),
                        Label.of("Other info", footer.build()))
                .build();
        event.replyModal(editLoreModal).queue();
    }

    @ModalHandler("gmLoreSave")
    public static void saveLoreFromModal(ModalInteractionEvent event, Game game) {
        String[] targets = event.getValue(Constants.POSITION).getAsString().split(",");
        String loreText = event.getValue(Constants.MESSAGE).getAsString();
        String footerText = event.getValue("footer").getAsString();
        Map<String, String[]> savedLoreMap = getSavedLore(game);

        // Validate
        for (int i = 0; i < targets.length; i++) {
            String validatedTarget = validateLore(targets[i], loreText, footerText, savedLoreMap, game);
            if (validatedTarget == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), targets[i] + " is invalid to save lore");
                continue;
            }
            targets[i] = validatedTarget;
        }

        // Construct
        StringBuilder sb = new StringBuilder();
        for (String target : targets) {
            setLore(target, loreText, footerText, savedLoreMap, game, sb);
        }

        saveLore(game, savedLoreMap);
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }

    private static String validateLore(
            String target, String loreText, String footerText, Map<String, String[]> savedLoreMap, Game game) {
        target = target.trim();
        if (savedLoreMap.containsKey(target) && StringUtils.isBlank(loreText)) {
            return target; // Deleting existing lore is always valid
        }

        if (loreText.length() > LORE_TEXT_MAX_LENGTH || footerText.length() > FOOTER_TEXT_MAX_LENGTH) {
            return null; // Too long
        }

        if (PositionMapper.isTilePositionValid(target)) {
            if (game.getTileByPosition(target) == null) {
                return null;
            }

            return target;
        } else {
            PlanetModel planet = Mapper.getPlanet(AliasHandler.resolvePlanet(target));
            if (planet == null || !game.getPlanets().contains(planet.getID())) {
                return null;
            }

            return planet.getID();
        }
    }

    public static void addLore(String target, String loreText, String footerText, Game game) {
        Map<String, String[]> savedLoreMap = getSavedLore(game);
        setLore(target, loreText, footerText, savedLoreMap, game, new StringBuilder());
        saveLore(game, savedLoreMap);
    }

    private static void setLore(
            String target,
            String loreText,
            String footerText,
            Map<String, String[]> savedLoreMap,
            Game game,
            StringBuilder sb) {
        if (StringUtils.isBlank(loreText)) {
            savedLoreMap.remove(target);
            sb.append("Removed Lore from ").append(target).append("\n");
        } else {
            savedLoreMap.put(target, new String[] {clean(loreText), clean(footerText)});
            sb.append("Set Lore to ").append(target).append("\n");
        }
    }

    private static String clean(String input) {
        return input == null ? "" : input.trim().replace(";", "").replace("|", "");
    }

    private static void saveLore(Game game, Map<String, String[]> lore) {
        String loreString = lore.entrySet().stream()
                .map(entry -> entry.getKey() + ";" + entry.getValue()[0] + ";" + entry.getValue()[1])
                .collect(Collectors.joining("|"));
        game.setStoredValue(SYSTEM_LORE_KEY, loreString);
    }

    public static void clearLore(Game game) {
        game.removeStoredValue(SYSTEM_LORE_KEY);
    }

    private static MessageEmbed buildLoreEmbed(Game game, String target, String[] lore, boolean isSystemLore) {
        Tile tile = isSystemLore ? game.getTileByPosition(target) : game.getTileFromPlanet(target);
        PlanetModel planet = isSystemLore ? null : Mapper.getPlanet(target);
        String titleTile = "";
        if (isSystemLore && tile != null && tile.getTileModel() != null) {
            titleTile = target + " - " + tile.getTileModel().getNameNullSafe() + " "
                    + tile.getTileModel().getEmoji();
        } else if (planet != null) {
            titleTile = planet.getName() + " " + planet.getEmoji();
        }

        Color embedColor = Color.black;
        if (tile != null && tile.getTileModel() != null) {
            switch (tile.getTileModel().getTileBack()) {
                case RED -> embedColor = Color.red;
                case BLUE -> embedColor = Color.blue;
                case GREEN -> embedColor = Color.green;
                default -> embedColor = Color.black;
            }
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("⭐ Lore of " + titleTile);
        eb.setDescription(lore[0]);
        eb.setFooter(lore[1]);
        eb.setColor(embedColor);
        return eb.build();
    }

    public static boolean hasLoreToShow(Game game, String target) {
        return game.isFowMode() && getSavedLore(game).containsKey(target);
    }

    public static void showSystemLore(Player player, Game game, String position) {
        if (!hasLoreToShow(game, position)) return;

        if (!FoWHelper.playerHasUnitsInSystem(player, game.getTileByPosition(position))) {
            return;
        }

        showLore(player, game, position, true);
    }

    public static void showPlanetLore(Player player, Game game, String planet) {
        if (!hasLoreToShow(game, planet)) return;

        showLore(player, game, planet, false);
    }

    private static void showLore(Player player, Game game, String target, boolean isSystemLore) {
        if (!player.isRealPlayer()) return;

        Map<String, String[]> lore = getSavedLore(game);
        if (lore.isEmpty() || !lore.containsKey(target)) {
            return;
        }

        MessageEmbed embed = buildLoreEmbed(game, target, lore.get(target), isSystemLore);
        MessageHelper.sendMessageToChannelWithEmbed(player.getPrivateChannel(), "You found a Lore Fragment", embed);

        GMService.logPlayerActivity(
                game, player, player.getRepresentationUnfoggedNoPing() + " was shown the lore of " + target);

        lore.remove(target);
        saveLore(game, lore);
    }
}
