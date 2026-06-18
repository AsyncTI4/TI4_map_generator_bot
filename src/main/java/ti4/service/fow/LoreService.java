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
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.RandomHelper;
import ti4.helpers.SortHelper;
import ti4.helpers.URLReaderHelper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;

public final class LoreService {

    private static final Map<String, Map<String, LoreEntry>> LORECACHE = new HashMap<>();

    private static final List<Button> LORE_BUTTONS = Arrays.asList(
            Buttons.blue("gmLoreAdd", "Add Lore"),
            Buttons.gray("gmLoreExportImportButtons", "Export/Import"),
            Buttons.gray("gmLoreRefresh", "Refresh"));

    private static final List<Button> LORE_EXPORT_IMPORT_BUTTONS = Arrays.asList(
            Buttons.gray("gmLoreExport", "Export"),
            Buttons.gray("gmLoreImport~MDL", "Import"),
            Buttons.DONE_DELETE_BUTTONS);

    private static final String SYSTEM_LORE_KEY = "fowSystemLore";
    // TODO: export file is written to the JVM working directory and never deleted after sending.
    private static final String LORE_EXPORT_FILENAME = "_lore_export.txt";
    private static final int LORE_TEXT_MAX_LENGTH = 1000;
    private static final int FOOTER_TEXT_MAX_LENGTH = 200;
    // TODO: editing existing lore does not re-validate RECEIVER against current game mode; an entry saved in FoW
    //       mode with ADJACENT/GM receiver remains editable without correction in non-FoW mode.
    // TODO: LORECACHE is a plain HashMap — not thread-safe and never evicted when a game ends.

    public enum RECEIVER {
        CURRENT("Current Player"),
        ADJACENT("Adjacent Players"),
        ALL("All Players"),
        GM("GM"),
        CARDS("Private Card Thread"),
        WINNER("Battle winner"),
        LOSER("Battle loser");
        public final String name;

        RECEIVER(String name) {
            this.name = name;
        }
    }

    public enum TRIGGER {
        ACTIVATED("Target is activated"),
        MOVED("Units are moved in"),
        CONTROLLED("Target is in control"),
        SPACE_BATTLE("A space battle was fought here"),
        GROUND_BATTLE("A ground battle was fought here");
        public final String name;

        TRIGGER(String name) {
            this.name = name;
        }
    }

    public enum PING {
        NO("No"),
        YES("Yes");
        public final String name;

        PING(String name) {
            this.name = name;
        }
    }

    public enum PERSISTANCE {
        ONCE("Once"),
        ALWAYS("Every time");
        public final String name;

        PERSISTANCE(String name) {
            this.name = name;
        }
    }

    public static class LoreEntry {
        public String target;
        public final String loreText;
        public String footerText = "";
        public RECEIVER receiver = RECEIVER.CURRENT;
        public TRIGGER trigger = TRIGGER.CONTROLLED;
        public PING ping = PING.NO;
        public PERSISTANCE persistance = PERSISTANCE.ONCE;

        public LoreEntry(String loreText) {
            this.loreText = loreText;
        }

        public static LoreEntry fromString(String loreString) {
            String[] splitLore = loreString.split(";");
            LoreEntry entry = new LoreEntry(splitLore[1]);
            entry.target = splitLore[0].trim();
            entry.footerText = splitLore.length > 2 ? splitLore[2] : "";
            try {
                entry.receiver = RECEIVER.valueOf(splitLore[3]);
                entry.trigger = TRIGGER.valueOf(splitLore[4]);
                entry.ping = PING.valueOf(splitLore[5]);
                entry.persistance = PERSISTANCE.valueOf(splitLore[6]);
            } catch (Exception e) {
                // Ignore invalid entries and use defaults
            }
            return entry;
        }

        @Override
        public String toString() {
            return target + ";" + loreText + ";" + footerText + ";" + receiver + ";" + trigger + ";" + ping + ";"
                    + persistance;
        }

        /** Footer lines beginning with '!' are machine-readable effects, not shown in the embed. */
        public List<String> getEffectLines() {
            List<String> out = new ArrayList<>();
            for (String line : footerText.split("\n")) {
                // Support multiple effects on one line: "!tg +2 !fleet +1"
                for (String segment : line.split("(?<=\\s)(?=!)")) {
                    String trimmed = segment.strip();
                    if (trimmed.startsWith("!")) {
                        out.add(trimmed.substring(1).strip());
                    }
                }
            }
            return out;
        }

        /** Footer text with effect lines removed, for display. */
        public String getDisplayFooter() {
            StringBuilder sb = new StringBuilder();
            for (String line : footerText.split("\n")) {
                // Strip effect segments from the line (same split as getEffectLines)
                StringBuilder lineOut = new StringBuilder();
                for (String segment : line.split("(?<=\\s)(?=!)")) {
                    String trimmed = segment.strip();
                    if (!trimmed.startsWith("!")) {
                        if (lineOut.length() > 0) lineOut.append(' ');
                        lineOut.append(trimmed);
                    }
                }
                String displayLine = lineOut.toString().strip();
                if (!displayLine.isEmpty()) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(displayLine);
                }
            }
            return sb.toString().strip();
        }
    }

    @ButtonHandler(value = "gmLoreRefresh", save = false)
    private static void refreshLoreButtons(ButtonInteractionEvent event, String buttonID, Game game) {
        showLoreButtons(event, buttonID, game);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler(value = "gmLoreExportImportButtons", save = false)
    private static void gmLoreExportImportButtons(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "", LORE_EXPORT_IMPORT_BUTTONS);
    }

    @ButtonHandler(value = "gmLoreExport", save = false)
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
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler(value = "gmLoreImport~MDL", save = false)
    public static void importLore(ButtonInteractionEvent event, Game game) {
        TextInput.Builder url = TextInput.create("url", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("http://your.url/fow123_lore_export.txt");

        Modal importLoreModal = Modal.create("gmLoreImportFromURL", "Import Lore from URL")
                .addComponents(Label.of("URL", url.build()))
                .build();
        event.replyModal(importLoreModal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("gmLoreImportFromURL")
    public static void importLoreFromURL(ModalInteractionEvent event, Game game) {
        String url = event.getValue("url").getAsString();
        String loreString = URLReaderHelper.readFromURL(url, event.getChannel());
        if (loreString == null) {
            return;
        }

        Map<String, LoreEntry> importedLore = readLore(loreString);
        // Validate
        List<String> effectProblems = new ArrayList<>();
        for (Map.Entry<String, LoreEntry> entry : importedLore.entrySet()) {
            String validatedTarget = validateLore(entry.getKey(), entry.getValue(), importedLore, game);
            if (validatedTarget == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), entry.getKey() + " is invalid to import lore");
                return;
            }
            for (String problem : LoreEffects.validateEffects(entry.getValue(), game)) {
                effectProblems.add(entry.getKey() + " — " + problem);
            }
        }

        // Construct
        getGameLore(game).putAll(importedLore);
        saveLore(game);

        StringBuilder result = new StringBuilder(importedLore.size() + " lore entries imported.");
        if (!effectProblems.isEmpty()) {
            result.append("\n⚠️ Effect warnings (imported anyway; these lines are skipped until fixed):");
            for (String problem : effectProblems) {
                result.append("\n• ").append(problem);
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), result.toString());
    }

    @ButtonHandler(value = "gmLore", save = false)
    public static void showLoreButtons(GenericInteractionCreateEvent event, String buttonID, Game game) {
        String page = StringUtils.substringAfter(buttonID, "page");
        int pageNum = StringUtils.isBlank(page) ? 1 : Integer.parseInt(page);
        List<ActionRow> buttons = Buttons.paginateButtons(getLoreButtons(game), LORE_BUTTONS, pageNum, "gmLore");

        if (StringUtils.isBlank(page)) {
            event.getMessageChannel()
                    .sendMessage("### Lore Management")
                    .setComponents(buttons)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        } else {
            ((IDeferrableCallback) event)
                    .getHook()
                    .editOriginalComponents(buttons)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    private static List<Button> getLoreButtons(Game game) {
        List<Button> loreButtons = new ArrayList<>();
        for (String target : getGameLore(game).keySet()) {
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

    public static Map<String, LoreEntry> getGameLore(Game game) {
        if (!LORECACHE.containsKey(game.getName())) {
            LORECACHE.put(game.getName(), readLore(game.getStoredValue(SYSTEM_LORE_KEY)));
        }
        return LORECACHE.get(game.getName());
    }

    // position;loreText;footerText;receiver;trigger;ping;persistance|...
    private static Map<String, LoreEntry> readLore(String loreString) {
        Map<String, LoreEntry> loreMap = new HashMap<>();
        if (StringUtils.isNotBlank(loreString)) {
            for (String savedLore : loreString.split("\\|")) {
                LoreEntry entry = LoreEntry.fromString(savedLore);
                loreMap.put(entry.target, entry);
            }
        }
        return loreMap;
    }

    private static String parseCurrentSelections(String menuId) {
        if (StringUtils.isBlank(menuId)) {
            return "";
        }
        String[] selectedValues = menuId.split(":");

        StringBuilder sb = new StringBuilder("\n");
        if (selectedValues.length > 0) {
            RECEIVER receiver = RECEIVER.valueOf(selectedValues[0]);
            sb.append("1. Notify... ").append(receiver.name).append('\n');
        }
        if (selectedValues.length > 1) {
            TRIGGER trigger = TRIGGER.valueOf(selectedValues[1]);
            sb.append("2. When... ").append(trigger.name).append('\n');
        }
        if (selectedValues.length > 2) {
            PING ping = PING.valueOf(selectedValues[2]);
            sb.append("3. Ping GM... ").append(ping.name).append('\n');
        }
        if (selectedValues.length > 3) {
            PERSISTANCE persistance = PERSISTANCE.valueOf(selectedValues[3]);
            sb.append("4. Trigger... ").append(persistance.name).append('\n');
        }

        return sb.toString();
    }

    private static void showNextSelection(GenericInteractionCreateEvent event, int step, StringSelectMenu menu) {
        event.getMessageChannel()
                .sendMessage("Add new Lore - Option " + step
                        + parseCurrentSelections(
                                StringUtils.substringAfter(menu.getCustomId(), "loreAdd" + step + "_")))
                .addComponents(List.of(ActionRow.of(menu)))
                .queue(Consumers.nop(), BotLogger::catchRestError);
        if (event instanceof ButtonInteractionEvent) {
            ((ComponentInteraction) event).getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        } else if (event instanceof StringSelectInteractionEvent) {
            ((ComponentInteraction) event).getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    @ButtonHandler("loreAdd0")
    public static void addLoreStep1(ButtonInteractionEvent event, Game game) {
        StringSelectMenu.Builder selectMenu =
                StringSelectMenu.create("loreAdd1_").setPlaceholder("Notify...");
        for (RECEIVER value : RECEIVER.values()) {
            if (!game.isFowMode() && (value == RECEIVER.GM || value == RECEIVER.ADJACENT)) continue;
            if (game.isFowMode() && value == RECEIVER.CARDS) continue;
            selectMenu.addOption(value.name, value.toString());
        }

        showNextSelection(event, 1, selectMenu.build());
    }

    @SelectionHandler("loreAdd1")
    public static void handleReceiverSelectionChange(StringSelectInteractionEvent event, String menuId) {
        RECEIVER selected = RECEIVER.valueOf(event.getValues().getFirst());
        StringSelectMenu.Builder selectMenu =
                StringSelectMenu.create("loreAdd2_" + selected).setPlaceholder("When...");
        for (TRIGGER value : TRIGGER.values()) {
            selectMenu.addOption(value.name, value.toString());
        }

        showNextSelection(event, 2, selectMenu.build());
    }

    @SelectionHandler("loreAdd2")
    public static void handleTriggerSelectionChange(StringSelectInteractionEvent event, String menuId, Game game) {
        String selected =
                menuId.replace("loreAdd2_", "") + ":" + event.getValues().getFirst();
        StringSelectMenu.Builder selectMenu =
                StringSelectMenu.create("loreAdd3_" + selected).setPlaceholder("Ping GM...");
        for (PING value : PING.values()) {
            if (!game.isFowMode() && value == PING.YES) continue;
            selectMenu.addOption(value.name, value.toString());
        }

        showNextSelection(event, 3, selectMenu.build());
    }

    @SelectionHandler("loreAdd3")
    public static void handlePingSelectionChange(StringSelectInteractionEvent event, String menuId) {
        String selected =
                menuId.replace("loreAdd3_", "") + ":" + event.getValues().getFirst();
        StringSelectMenu.Builder selectMenu =
                StringSelectMenu.create("loreAdd4_" + selected).setPlaceholder("Trigger...");
        for (PERSISTANCE value : PERSISTANCE.values()) {
            selectMenu.addOption(value.name, value.toString());
        }

        showNextSelection(event, 4, selectMenu.build());
    }

    @SelectionHandler("loreAdd4")
    public static void handlePersistanceSelectionChange(StringSelectInteractionEvent event, String menuId) {
        String selected =
                menuId.replace("loreAdd4_", "") + ":" + event.getValues().getFirst();
        confirmAddLoreSettings(event.getChannel(), selected);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("gmLoreAdd")
    public static void addDefaultLore(ButtonInteractionEvent event, Game game) {
        confirmAddLoreSettings(
                event.getChannel(),
                RECEIVER.CURRENT + ":" + TRIGGER.CONTROLLED + ":" + PING.NO + ":" + PERSISTANCE.ONCE);
    }

    private static void confirmAddLoreSettings(MessageChannel channel, String selectedValues) {
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                "Add new Lore with" + parseCurrentSelections(selectedValues),
                Arrays.asList(
                        Buttons.blue("gmLoreEdit_NEW_" + selectedValues + "~MDL", "Add Lore"),
                        Buttons.red("loreAdd0", "Change options")));
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
        TextInput.Builder footer = TextInput.create("footer", TextInputStyle.PARAGRAPH)
                .setRequired(false)
                .setPlaceholder("!tg +2\n!comms +3\n!ac 2\n!fleet +1\n!plastic red 2 infantry mr\n!token gravityrift")
                .setMaxLength(FOOTER_TEXT_MAX_LENGTH);

        String selections = target.replace("NEW_", "");
        if (!target.startsWith("NEW")) {
            LoreEntry entry = getGameLore(game).get(target);
            position.setValue(entry.target);
            lore.setValue(entry.loreText);
            if (!StringUtils.isBlank(entry.footerText)) {
                footer.setValue(entry.footerText);
            }
            selections = entry.receiver + ":" + entry.trigger + ":" + entry.ping + ":" + entry.persistance;
        }

        Modal editLoreModal = Modal.create("gmLoreSave_" + selections, "Add Lore")
                .addComponents(
                        Label.of("Target", position.build()),
                        Label.of("Lore (clear to delete)", lore.build()),
                        Label.of("Other info", footer.build()))
                .build();
        event.replyModal(editLoreModal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("gmLoreSave_")
    public static void saveLoreFromModal(ModalInteractionEvent event, Game game) {
        String[] targets = event.getValue(Constants.POSITION).getAsString().split(",");
        String[] selectedOptions = event.getModalId().replace("gmLoreSave_", "").split(":");
        Map<String, LoreEntry> loreMap = getGameLore(game);

        LoreEntry newEntry =
                new LoreEntry(clean(event.getValue(Constants.MESSAGE).getAsString()));
        newEntry.footerText = clean(event.getValue("footer").getAsString());
        newEntry.receiver = RECEIVER.valueOf(selectedOptions[0]);
        newEntry.trigger = TRIGGER.valueOf(selectedOptions[1]);
        newEntry.ping = PING.valueOf(selectedOptions[2]);
        newEntry.persistance = PERSISTANCE.valueOf(selectedOptions[3]);

        // Validate
        List<String> validTargets = new ArrayList<>();
        for (String s : targets) {
            String validatedTarget = validateLore(s, newEntry, loreMap, game);
            if (validatedTarget == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), s + " is invalid to save lore");
                continue;
            }
            validTargets.add(validatedTarget);
        }

        // Construct
        StringBuilder sb = new StringBuilder();
        for (String target : validTargets) {
            newEntry.target = target;
            setLore(newEntry, game, sb);
        }

        if (!validTargets.isEmpty()) {
            saveLore(game);
        }

        List<String> effectProblems = LoreEffects.validateEffects(newEntry, game);
        if (!effectProblems.isEmpty()) {
            sb.append("\n⚠️ Effect warnings (lore still saved; these lines are skipped until fixed):");
            for (String problem : effectProblems) {
                sb.append("\n• ").append(problem);
            }
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }

    private static String validateLore(
            String target, LoreEntry loreEntry, Map<String, LoreEntry> savedLoreMap, Game game) {
        target = target.trim();
        if (savedLoreMap.containsKey(target) && StringUtils.isBlank(loreEntry.loreText)) {
            return target; // Deleting existing lore is always valid
        }

        if (loreEntry.loreText.length() > LORE_TEXT_MAX_LENGTH
                || loreEntry.footerText.length() > FOOTER_TEXT_MAX_LENGTH) {
            return null; // Too long
        }

        if (PositionMapper.isTilePositionValid(target)) {
            if (game.getTileByPosition(target) == null) {
                return null;
            }

            return target;
        } else {
            PlanetModel planet = Mapper.getPlanet(AliasHandler.resolvePlanet(target.replace(" ", "")));
            if (planet == null || !game.getPlanets().contains(planet.getID())) {
                return null;
            }

            return planet.getID();
        }
    }

    public static void addLoreFromString(String loreString, Game game) {
        LoreEntry newEntry = readLore(loreString).values().iterator().next();
        setLore(newEntry, game, new StringBuilder());
        saveLore(game);
    }

    private static void setLore(LoreEntry entry, Game game, StringBuilder sb) {
        Map<String, LoreEntry> gameLoreMap = getGameLore(game);
        if (StringUtils.isBlank(entry.loreText)) {
            gameLoreMap.remove(entry.target);
            sb.append("Removed Lore from `").append(entry.target).append("`\n");
        } else {
            gameLoreMap.put(entry.target, entry);
            sb.append("Set Lore to `").append(entry.target).append("`\n");
        }
    }

    public static String clean(String input) {
        return input == null ? "" : input.trim().replace(";", "").replace("|", "");
    }

    private static void saveLore(Game game) {
        String loreString =
                getGameLore(game).values().stream().map(LoreEntry::toString).collect(Collectors.joining("|"));
        game.setStoredValue(SYSTEM_LORE_KEY, loreString);
    }

    public static void clearLore(Game game) {
        getGameLore(game).clear();
        game.removeStoredValue(SYSTEM_LORE_KEY);
    }

    private static final List<String> UNKNOWN_LORE_TARGETS = Arrays.asList(
            "from Deep Unknown",
            "from Silent Beyond",
            "from Hidden Realms",
            "from Shrouded Expanse",
            "from Dark Horizon",
            "from Nameless Void",
            "from Fading Echoes",
            "from Forgotten Reach",
            "from Distant Silence",
            "from Elsewhere",
            "from Afar",
            "from Beyond",
            "from Nowhere",
            "of Nameless Origins",
            "of Shrouded Mystery",
            "of Unknown Depths",
            "of Silent Vastness",
            "of Hidden Secrets",
            "of Dark Enigmas",
            "of Fading Legends",
            "of Forgotten Tales",
            "of Unseen Realms",
            "of No Known Origin");

    private static MessageEmbed buildLoreEmbed(Game game, String target, LoreEntry lore, boolean isSystemLore) {
        Tile tile = isSystemLore ? game.getTileByPosition(target) : game.getTileFromPlanet(target);
        PlanetModel planet = isSystemLore ? null : Mapper.getPlanet(target);
        String titleTile = "of ";
        if (lore.receiver == RECEIVER.ALL) {
            titleTile = RandomHelper.pickRandomFromList(UNKNOWN_LORE_TARGETS);
        } else if (isSystemLore && tile != null && tile.getTileModel() != null) {
            titleTile += target + " - " + tile.getTileModel().getNameNullSafe() + " "
                    + tile.getTileModel().getEmoji();
        } else if (planet != null) {
            titleTile += planet.getName() + " " + planet.getEmoji();
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
        eb.setTitle("⭐ Lore " + titleTile);
        eb.setDescription(lore.loreText);
        eb.setFooter(lore.getDisplayFooter());
        eb.setColor(embedColor);
        return eb.build();
    }

    private static boolean hasLoreToShow(Game game, String target, TRIGGER trigger) {
        return getGameLore(game).containsKey(target) && getGameLore(game).get(target).trigger == trigger;
    }

    public static void showSpaceBattleLore(Player activePlayer, Game game, String position) {
        showSystemLore(activePlayer, game, position, TRIGGER.SPACE_BATTLE);
        for (Player other : game.getRealPlayers()) {
            if (other == activePlayer) continue;
            if (!game.getStoredValue("combatRoundTracker" + other.getFaction() + position + Constants.SPACE)
                    .isEmpty()) {
                showSystemLore(other, game, position, TRIGGER.SPACE_BATTLE);
            }
        }
    }

    public static void showSystemLore(Player player, Game game, String position, TRIGGER trigger) {
        if (!hasLoreToShow(game, position, trigger)) return;

        if (trigger == TRIGGER.SPACE_BATTLE) {
            // Only fire when combat rounds were actually rolled in this system
            if (game.getStoredValue("combatRoundTracker" + player.getFaction() + position + Constants.SPACE)
                    .isEmpty()) {
                return;
            }
        } else if (trigger != TRIGGER.ACTIVATED
                && !FoWHelper.playerHasUnitsInSystem(player, game.getTileByPosition(position))) {
            return;
        }

        showLore(player, game, position, true);
    }

    public static void showPlanetLore(Player player, Game game, String planet, TRIGGER trigger) {
        if (!hasLoreToShow(game, planet, trigger)) return;

        // CONTROLLED requires planet control; MOVED requires units on planet; GROUND_BATTLE has no extra guard
        if (trigger == TRIGGER.CONTROLLED && !player.getPlanets().contains(planet)
                || trigger == TRIGGER.MOVED
                        && !FoWHelper.playerHasUnitsOnPlanet(player, game.getTileFromPlanet(planet), planet)) {
            return;
        }

        showLore(player, game, planet, false);
    }

    private static void showLore(Player player, Game game, String target, boolean isSystemLore) {
        if (!player.isRealPlayer()) return;

        Map<String, LoreEntry> gameLore = getGameLore(game);
        if (gameLore.isEmpty() || !gameLore.containsKey(target)) {
            return;
        }
        LoreEntry loreEntry = gameLore.get(target);

        String position = target;
        if (!isSystemLore) {
            Tile tile = game.getTileFromPlanet(target);
            if (tile != null) {
                position = tile.getPosition();
            }
        }

        // WINNER/LOSER: check unit presence to gate delivery
        if (loreEntry.receiver == RECEIVER.WINNER || loreEntry.receiver == RECEIVER.LOSER) {
            boolean hasUnits;
            if (isSystemLore) {
                hasUnits = FoWHelper.playerHasUnitsInSystem(player, game.getTileByPosition(target));
            } else {
                hasUnits = FoWHelper.playerHasUnitsOnPlanet(player, game.getTileFromPlanet(target), target);
            }
            if (loreEntry.receiver == RECEIVER.WINNER && !hasUnits) return;
            if (loreEntry.receiver == RECEIVER.LOSER && hasUnits) return;
        }

        Map<MessageChannel, String> channels = new HashMap<>();
        String who = player.getRepresentation() + " ";
        switch (loreEntry.receiver) {
            case CURRENT, WINNER, LOSER:
                channels.put(player.getCorrectChannel(), player.getRepresentationUnfogged());
                break;
            case ADJACENT:
                for (Player p : FoWHelper.getAdjacentPlayers(game, position, false)) {
                    channels.put(p.getCorrectChannel(), p.getRepresentationUnfogged());
                }
                break;
            case ALL:
                channels.put(game.getMainGameChannel(), game.getPing());
                who = "Someone ";
                break;
            case GM:
                channels.put(GMService.getGMChannel(game), GMService.gmPing(game));
                who = player.getRepresentationUnfoggedNoPing() + " ";
                break;
            case CARDS:
                var cardsThread = player.getCardsInfoThread();
                if (cardsThread != null) {
                    channels.put(cardsThread, player.getRepresentationUnfogged());
                }
                break;
        }

        MessageEmbed embed = buildLoreEmbed(game, target, loreEntry, isSystemLore);
        for (Map.Entry<MessageChannel, String> entry : channels.entrySet()) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    entry.getKey(), entry.getValue() + ", Lore was discovered by " + who, embed);
        }

        LoreEffects.EffectResults results =
                LoreEffects.applyLoreEffects(player, game, loreEntry, position, isSystemLore);
        if (!results.mapChanges().isEmpty()) {
            if (game.isFowMode()) {
                // Each map change pings the system it affected so all players with visibility are notified.
                // The triggering player is sent directly (pingSystem skips their color in the message).
                MessageChannel privateChannel = player.getPrivateChannel();
                MessageChannel gmChannel = GMService.getGMChannel(game);
                for (LoreEffects.EffectDescription desc : results.mapChanges()) {
                    String changeMsg = "**Map change from lore:** " + desc.text();
                    String pingPosition = desc.tilePosition() != null ? desc.tilePosition() : position;
                    FoWHelper.pingSystem(game, pingPosition, changeMsg, false);
                    if (privateChannel != null) MessageHelper.sendMessageToChannel(privateChannel, changeMsg);
                    if (gmChannel != null) MessageHelper.sendMessageToChannel(gmChannel, changeMsg);
                }
            } else {
                String changeMsg = "**Map changes from lore:**\n"
                        + results.mapChanges().stream()
                                .map(LoreEffects.EffectDescription::text)
                                .collect(Collectors.joining("\n"));
                for (MessageChannel channel : channels.keySet()) {
                    MessageHelper.sendMessageToChannel(channel, changeMsg);
                }
            }
        }
        if (!results.playerChanges().isEmpty()) {
            String changeMsg = "**Player changes from lore:**\n" + String.join("\n", results.playerChanges());
            if (game.isFowMode()) {
                // Sheet info is private in FoW: only the player and GM see these
                MessageChannel privateChannel = player.getPrivateChannel();
                if (privateChannel != null) MessageHelper.sendMessageToChannel(privateChannel, changeMsg);
                MessageChannel gmChannel = GMService.getGMChannel(game);
                if (gmChannel != null) MessageHelper.sendMessageToChannel(gmChannel, changeMsg);
            } else {
                for (MessageChannel channel : channels.keySet()) {
                    MessageHelper.sendMessageToChannel(channel, changeMsg);
                }
            }
        }

        GMService.logPlayerActivity(
                game,
                player,
                player.getRepresentationUnfoggedNoPing() + " discovered lore of " + target,
                null,
                loreEntry.ping == PING.YES);

        if (loreEntry.persistance == PERSISTANCE.ONCE) {
            gameLore.remove(target);
            saveLore(game);
        }
    }
}
