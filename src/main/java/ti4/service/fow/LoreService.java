package ti4.service.fow;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import ti4.helpers.ButtonHelper;
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
        ALWAYS("Every time"),
        ONCE_PER_PLAYER("Once per player");
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

        private static final String CHOICE_MARKER = "!choice";

        /**
         * A footer line that is just {@code !choice} gates the whole entry behind an Accept/Reject
         * confirmation sent individually to each recipient — see {@code LoreService#requestChoiceConfirmation}.
         */
        public boolean isChoiceGated() {
            for (String line : footerText.split("\n")) {
                if (line.strip().equalsIgnoreCase(CHOICE_MARKER)) return true;
            }
            return false;
        }

        /** Strips a leading "accept:"/"reject:" tag (case-insensitive) from a footer line, if present. */
        private static String stripBranchTag(String line) {
            String lower = line.toLowerCase();
            if (lower.startsWith("accept:") || lower.startsWith("reject:")) {
                return line.substring(7).strip();
            }
            return line;
        }

        /**
         * Footer lines beginning with '!' are machine-readable effects, not shown in the embed.
         * A line prefixed with "accept:"/"reject:" only fires when a {@code !choice} gate is resolved
         * that way; the tag is preserved on the returned line for {@link LoreEffects} to parse.
         */
        public List<String> getEffectLines() {
            List<String> out = new ArrayList<>();
            for (String line : footerText.split("\n")) {
                String stripped = line.strip();
                if (stripped.equalsIgnoreCase(CHOICE_MARKER)) continue;

                String lower = stripped.toLowerCase();
                String tag = lower.startsWith("accept:") ? "accept:" : lower.startsWith("reject:") ? "reject:" : "";
                String rest = tag.isEmpty() ? stripped : stripBranchTag(stripped);

                // Support multiple effects on one line: "!tg +2 !fleet +1"
                for (String segment : rest.split("(?<=\\s)(?=!)")) {
                    String trimmed = segment.strip();
                    if (trimmed.startsWith("!")) {
                        out.add(tag + trimmed.substring(1).strip());
                    }
                }
            }
            return out;
        }

        /** Footer text with the !choice marker and effect lines removed, for display. */
        public String getDisplayFooter() {
            StringBuilder sb = new StringBuilder();
            for (String line : footerText.split("\n")) {
                String stripped = line.strip();
                if (stripped.equalsIgnoreCase(CHOICE_MARKER)) continue;
                stripped = stripBranchTag(stripped);

                // Strip effect segments from the line (same split as getEffectLines)
                StringBuilder lineOut = new StringBuilder();
                for (String segment : stripped.split("(?<=\\s)(?=!)")) {
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

        ImportResult result = parseLoreImport(loreString, game);

        if (!result.entries().isEmpty()) {
            getGameLore(game).putAll(result.entries());
            saveLore(game);
        }

        StringBuilder sb = new StringBuilder(result.entries().size() + " lore entries imported.");
        if (!result.errors().isEmpty()) {
            sb.append("\n❌ Entries skipped (not imported):");
            for (String error : result.errors()) {
                sb.append("\n• ").append(error);
            }
        }
        if (!result.warnings().isEmpty()) {
            sb.append("\n⚠️ Effect warnings (imported anyway; these lines are skipped until fixed):");
            for (String warning : result.warnings()) {
                sb.append("\n• ").append(warning);
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }

    /** {@code entries} only contains entries that parsed and validated cleanly; {@code errors} entries were skipped. */
    record ImportResult(Map<String, LoreEntry> entries, List<String> errors, List<String> warnings) {}

    /**
     * Parses an export-format string entry by entry, tolerating bad entries instead of aborting the whole
     * import: a malformed line, an unrecognized enum value, or an invalid target only drops that one entry
     * and is reported with its 1-based position in the file so a GM can find and fix it.
     */
    static ImportResult parseLoreImport(String loreString, Game game) {
        Map<String, LoreEntry> validEntries = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (StringUtils.isBlank(loreString)) {
            return new ImportResult(validEntries, errors, warnings);
        }

        String[] rawEntries = loreString.split("\\|");
        for (int i = 0; i < rawEntries.length; i++) {
            String raw = rawEntries[i];
            if (StringUtils.isBlank(raw)) continue;
            String label = "entry #" + (i + 1);

            String[] fields = raw.split(";");
            if (fields.length < 2) {
                errors.add(label + ": malformed — expected at least \"target;loreText\", found " + fields.length
                        + " field(s)");
                continue;
            }

            String badEnumField = findBadEnumField(fields);
            if (badEnumField != null) {
                errors.add(label + " (`" + fields[0].trim() + "`): " + badEnumField);
                continue;
            }

            LoreEntry entry = LoreEntry.fromString(raw);
            String rawTarget = fields[0].trim();
            label += " (`" + rawTarget + "`)";

            String validatedTarget = validateLore(rawTarget, entry, validEntries, game);
            if (validatedTarget == null) {
                errors.add(label + ": target is not a valid system/planet on this map, or lore/footer text exceeds"
                        + " the length limit");
                continue;
            }
            entry.target = validatedTarget;
            validEntries.put(validatedTarget, entry);

            for (String problem : LoreEffects.validateEffects(entry, game)) {
                warnings.add(label + " — " + problem);
            }
        }

        return new ImportResult(validEntries, errors, warnings);
    }

    /**
     * {@code LoreEntry.fromString} silently falls back to defaults on a bad RECEIVER/TRIGGER/PING/PERSISTANCE
     * name, which would otherwise import the entry with the wrong settings instead of flagging the typo.
     */
    private static String findBadEnumField(String[] fields) {
        if (fields.length > 3 && !isValidEnumName(RECEIVER.class, fields[3])) {
            return "unrecognized RECEIVER `" + fields[3] + "`";
        }
        if (fields.length > 4 && !isValidEnumName(TRIGGER.class, fields[4])) {
            return "unrecognized TRIGGER `" + fields[4] + "`";
        }
        if (fields.length > 5 && !isValidEnumName(PING.class, fields[5])) {
            return "unrecognized PING `" + fields[5] + "`";
        }
        if (fields.length > 6 && !isValidEnumName(PERSISTANCE.class, fields[6])) {
            return "unrecognized PERSISTANCE `" + fields[6] + "`";
        }
        return null;
    }

    private static <E extends Enum<E>> boolean isValidEnumName(Class<E> type, String name) {
        try {
            Enum.valueOf(type, name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
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

    static void showLore(Player player, Game game, String target, boolean isSystemLore) {
        if (!player.isRealPlayer()) return;

        Map<String, LoreEntry> gameLore = getGameLore(game);
        if (gameLore.isEmpty() || !gameLore.containsKey(target)) {
            return;
        }
        LoreEntry loreEntry = gameLore.get(target);

        if (loreEntry.persistance == PERSISTANCE.ONCE_PER_PLAYER
                && getStoredIdSet(game, loreDeliveredKey(target)).contains(player.getUserID())) {
            return;
        }

        String position = target;
        if (!isSystemLore) {
            Tile tile = game.getTileFromPlanet(target);
            if (tile != null) {
                position = tile.getPosition();
            }
        }

        // WINNER/LOSER: ask the player to self-report the outcome rather than inferring it from unit
        // presence (mutual annihilation / early retreat make presence sadly an unreliable proxy). NPCs
        // can't click buttons, so there's no reliable way to self-report on their behalf — skip rather
        // than guessing the outcome.
        if (loreEntry.receiver == RECEIVER.WINNER || loreEntry.receiver == RECEIVER.LOSER) {
            if (!player.isNpc()) {
                requestBattleResultConfirmation(player, target, isSystemLore);
            }
            return;
        }

        if (loreEntry.isChoiceGated()) {
            requestChoiceConfirmation(player, game, target, isSystemLore, loreEntry, position);
            return;
        }

        deliverLore(player, game, target, isSystemLore, loreEntry, position);
    }

    /**
     * Everyone the entry would normally notify gets their own independent Accept/Reject buttons and
     * their own personal reward — there's no shared "first click wins" state to fight over. CURRENT/CARDS
     * resolve to just the triggering player; ADJACENT/ALL fan out to every player in scope. GM has no
     * player audience (GMs never receive lore rewards), so a GM-receiver entry just delivers unconditionally.
     */
    static List<Player> computeChoiceAudience(
            Game game, LoreEntry loreEntry, Player triggeringPlayer, String position) {
        List<Player> audience =
                switch (loreEntry.receiver) {
                    case ADJACENT -> FoWHelper.getAdjacentPlayers(game, position, false);
                    case ALL -> game.getRealPlayers();
                    case GM -> List.of();
                    default -> List.of(triggeringPlayer);
                };
        // NPC-controlled seats don't get interactive buttons, so they're excluded from the audience
        // entirely rather than being left in it unresolved — otherwise they'd block round completion
        // in maybeCompleteChoiceRound forever.
        return audience.stream().filter(p -> !p.isNpc()).toList();
    }

    static String loreDeliveredKey(String target) {
        return "loreDeliveredTo_" + target;
    }

    static String choiceOfferedKey(String target) {
        return "loreChoiceOffered_" + target;
    }

    static String choiceResolvedKey(String target) {
        return "loreChoiceResolved_" + target;
    }

    static String choiceMapAppliedKey(String target) {
        return "loreChoiceMapApplied_" + target;
    }

    static Set<String> getStoredIdSet(Game game, String key) {
        String raw = game.getStoredValue(key);
        if (StringUtils.isBlank(raw)) return new HashSet<>();
        return new HashSet<>(Arrays.asList(raw.split(",")));
    }

    static void addStoredId(Game game, String key, String userID) {
        Set<String> set = getStoredIdSet(game, key);
        set.add(userID);
        game.setStoredValue(key, String.join(",", set));
    }

    private static final String[] LORE_USER_ID_KEY_PREFIXES = {
        "loreDeliveredTo_", "loreChoiceOffered_", "loreChoiceResolved_"
    };

    /**
     * Rewrites {@code oldUserId} to {@code newUserId} in every lore-related stored id-set (delivered/offered/
     * resolved tracking, all keyed by userID and comma-joined per target) — called from the {@code /replace}
     * flow so a seat's one-time/per-player lore progress survives the player being replaced, instead of the
     * new account silently getting a second shot at "once per player" rewards or choices.
     */
    public static void onPlayerReplaced(Game game, String oldUserId, String newUserId) {
        for (Map.Entry<String, String> entry : game.getStoredValueMap().entrySet()) {
            String key = entry.getKey();
            boolean isLoreIdKey = false;
            for (String prefix : LORE_USER_ID_KEY_PREFIXES) {
                if (key.startsWith(prefix)) {
                    isLoreIdKey = true;
                    break;
                }
            }
            if (!isLoreIdKey) continue;

            Set<String> ids = getStoredIdSet(game, key);
            if (ids.remove(oldUserId)) {
                ids.add(newUserId);
                game.setStoredValue(key, String.join(",", ids));
            }
        }
    }

    /** Sends each not-yet-offered recipient their own Accept/Reject buttons in their own channel. */
    static void requestChoiceConfirmation(
            Player triggeringPlayer,
            Game game,
            String target,
            boolean isSystemLore,
            LoreEntry loreEntry,
            String position) {
        List<Player> audience = computeChoiceAudience(game, loreEntry, triggeringPlayer, position);
        if (audience.isEmpty()) {
            // No player audience to offer the choice to (e.g. RECEIVER.GM) — fall back to unconditional delivery.
            deliverLore(triggeringPlayer, game, target, isSystemLore, loreEntry, position);
            return;
        }

        String scope = isSystemLore ? "sys" : "planet";
        MessageEmbed embed = buildLoreEmbed(game, target, loreEntry, isSystemLore);
        Set<String> alreadyOffered = getStoredIdSet(game, choiceOfferedKey(target));
        for (Player p : audience) {
            if (!p.isRealPlayer() || alreadyOffered.contains(p.getUserID())) continue;
            String acceptId = "loreChoice_accept_" + scope + "_" + p.getUserID() + "_" + target;
            String rejectId = "loreChoice_reject_" + scope + "_" + p.getUserID() + "_" + target;
            List<Button> buttons = Arrays.asList(Buttons.green(acceptId, "Accept"), Buttons.red(rejectId, "Reject"));
            MessageHelper.sendMessageToChannelWithEmbed(
                    p.getCorrectChannel(), p.getRepresentationUnfogged() + ", Lore was discovered.", embed);
            MessageHelper.sendMessageToChannel(
                    p.getCorrectChannel(), p.getRepresentation() + ", Accept or reject?", buttons);
            addStoredId(game, choiceOfferedKey(target), p.getUserID());
        }
    }

    @ButtonHandler("loreChoice_")
    private static void handleChoiceConfirmation(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        ButtonHelper.deleteMessage(event);

        String[] parts = buttonID.split("_", 5);
        String branch = parts[1];
        boolean isSystemLore = "sys".equals(parts[2]);
        String resolverUserID = parts[3];
        String target = parts[4];

        if (player == null || !resolverUserID.equals(player.getUserID())) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "This choice isn't yours to make.");
            return;
        }

        LoreEntry loreEntry = getGameLore(game).get(target);
        if (loreEntry == null) return;

        addStoredId(game, choiceResolvedKey(target), player.getUserID());

        String position = target;
        if (!isSystemLore) {
            Tile tile = game.getTileFromPlanet(target);
            if (tile != null) {
                position = tile.getPosition();
            }
        }
        deliverChoiceLore(player, game, target, isSystemLore, loreEntry, position, branch);
    }

    /** Delivers (or withholds) lore + its tagged effects to a single resolver based on their accept/reject pick. */
    static void deliverChoiceLore(
            Player resolver,
            Game game,
            String target,
            boolean isSystemLore,
            LoreEntry loreEntry,
            String position,
            String branch) {
        MessageChannel channel = resolver.getCorrectChannel();
        if ("reject".equals(branch)) {
            MessageHelper.sendMessageToChannel(
                    channel, resolver.getRepresentationUnfoggedNoPing() + " rejected the lore's choice. No reward.");
        } else {
            MessageEmbed embed = buildLoreEmbed(game, target, loreEntry, isSystemLore);
            MessageHelper.sendMessageToChannelWithEmbed(
                    channel, resolver.getRepresentationUnfogged() + " accepted the lore's choice!", embed);
        }

        // Each audience member resolves independently, but a map-mutating effect line must still apply
        // exactly once for the whole entry — whichever resolver gets there first triggers it; everyone
        // after only gets their own copy of the player-stat effects.
        boolean mapEffectsAlreadyApplied =
                !game.getStoredValue(choiceMapAppliedKey(target)).isEmpty();
        LoreEffects.EffectResults results = mapEffectsAlreadyApplied
                ? LoreEffects.applyLoreEffectsPlayerOnly(resolver, game, loreEntry, isSystemLore, branch)
                : LoreEffects.applyLoreEffects(resolver, game, loreEntry, isSystemLore, branch);
        if (!mapEffectsAlreadyApplied) {
            game.setStoredValue(choiceMapAppliedKey(target), "true");
        }
        reportEffectResults(resolver, game, results, position, channel != null ? Set.of(channel) : Set.of());

        GMService.logPlayerActivity(
                game,
                resolver,
                resolver.getRepresentationUnfoggedNoPing() + " resolved a lore choice for " + target + " (" + branch
                        + ")",
                null,
                loreEntry.ping == PING.YES);

        if (loreEntry.persistance == PERSISTANCE.ONCE_PER_PLAYER) {
            addStoredId(game, loreDeliveredKey(target), resolver.getUserID());
        }
        maybeCompleteChoiceRound(resolver, game, loreEntry, target, position);
    }

    /**
     * Once every member of the current audience has resolved this round of the choice, clear the round's
     * offered/resolved/map-applied bookkeeping. For {@code PERSISTANCE.ONCE} the entry is also removed for
     * good (the round was its only chance). For {@code ALWAYS}, clearing the bookkeeping is what lets the
     * choice be offered again — fresh — the next time the entry's trigger condition fires for any player;
     * {@code ONCE_PER_PLAYER} doesn't need special handling here since it's already blocked independently
     * at the top of {@link #showLore} via {@code loreDeliveredKey}.
     */
    static void maybeCompleteChoiceRound(
            Player resolver, Game game, LoreEntry loreEntry, String target, String position) {
        List<Player> currentAudience = computeChoiceAudience(game, loreEntry, resolver, position);
        Set<String> resolved = getStoredIdSet(game, choiceResolvedKey(target));
        boolean allResolved = !currentAudience.isEmpty()
                && currentAudience.stream()
                        .filter(Player::isRealPlayer)
                        .allMatch(p -> resolved.contains(p.getUserID()));
        if (allResolved) {
            if (loreEntry.persistance == PERSISTANCE.ONCE) {
                getGameLore(game).remove(target);
                saveLore(game);
            }
            game.removeStoredValue(choiceMapAppliedKey(target));
            game.removeStoredValue(choiceOfferedKey(target));
            game.removeStoredValue(choiceResolvedKey(target));
        }
    }

    /** Sends the "did you win or lose" buttons; delivery resumes in {@link #handleBattleResultConfirmation}. */
    private static void requestBattleResultConfirmation(Player player, String target, boolean isSystemLore) {
        String scope = isSystemLore ? "sys" : "planet";
        String wonButtonId = "loreBattleResult_won_" + scope + "_" + target;
        String lostButtonId = "loreBattleResult_lost_" + scope + "_" + target;
        List<Button> buttons = Arrays.asList(
                Buttons.green(wonButtonId, "I won this battle"), Buttons.red(lostButtonId, "I lost this battle"));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + ", did you win or lose this battle? Lore awaits your answer.",
                buttons);
    }

    @ButtonHandler("loreBattleResult_")
    private static void handleBattleResultConfirmation(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        ButtonHelper.deleteMessage(event);

        String[] parts = buttonID.split("_", 4);
        String outcome = parts[1];
        boolean isSystemLore = "sys".equals(parts[2]);
        String target = parts[3];

        LoreEntry loreEntry = getGameLore(game).get(target);
        if (loreEntry == null) return;

        boolean matches = (loreEntry.receiver == RECEIVER.WINNER && "won".equals(outcome))
                || (loreEntry.receiver == RECEIVER.LOSER && "lost".equals(outcome));
        if (!matches) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfoggedNoPing() + ", no lore for you this time.");
            return;
        }

        String position = target;
        if (!isSystemLore) {
            Tile tile = game.getTileFromPlanet(target);
            if (tile != null) {
                position = tile.getPosition();
            }
        }
        deliverLore(player, game, target, isSystemLore, loreEntry, position);
    }

    static void deliverLore(
            Player player, Game game, String target, boolean isSystemLore, LoreEntry loreEntry, String position) {
        Map<String, LoreEntry> gameLore = getGameLore(game);
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

        LoreEffects.EffectResults results = LoreEffects.applyLoreEffects(player, game, loreEntry, isSystemLore);
        reportEffectResults(player, game, results, position, channels.keySet());

        // ADJACENT/ALL notify multiple players, but the call above only ever rewards the triggering
        // player. Give every other recipient their own copy of the player-stat effects (map-mutating
        // effects already applied exactly once above and must not repeat).
        List<Player> extraRecipients =
                switch (loreEntry.receiver) {
                    case ADJACENT -> FoWHelper.getAdjacentPlayers(game, position, false);
                    case ALL -> game.getRealPlayers();
                    default -> List.of();
                };
        for (Player other : extraRecipients) {
            if (other.equals(player)) continue;
            LoreEffects.EffectResults otherResults =
                    LoreEffects.applyLoreEffectsPlayerOnly(other, game, loreEntry, isSystemLore, null);
            MessageChannel otherChannel = other.getCorrectChannel();
            reportEffectResults(
                    other, game, otherResults, position, otherChannel != null ? Set.of(otherChannel) : Set.of());
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
        } else if (loreEntry.persistance == PERSISTANCE.ONCE_PER_PLAYER) {
            addStoredId(game, loreDeliveredKey(target), player.getUserID());
        }
    }

    /**
     * Posts map/player-change summaries for an applied {@link LoreEffects.EffectResults}. In FoW, sheet
     * and map info is private: only the  acting player's private channel and the GM activity thread see
     * it. Outside FoW it's broadcast to every channel the lore was delivered to.
     */
    private static void reportEffectResults(
            Player player,
            Game game,
            LoreEffects.EffectResults results,
            String position,
            Set<MessageChannel> channels) {
        if (!results.mapChanges().isEmpty()) {
            if (game.isFowMode()) {
                // Each map change pings the system it affected so all players with visibility are notified.
                // The triggering player is sent directly (pingSystem skips their color in the message).
                MessageChannel privateChannel = player.getPrivateChannel();
                for (LoreEffects.EffectDescription desc : results.mapChanges()) {
                    String changeMsg = "**Map change from lore:** " + desc.text();
                    String pingPosition = desc.tilePosition() != null ? desc.tilePosition() : position;
                    FoWHelper.pingSystem(game, pingPosition, changeMsg, false);
                    if (privateChannel != null) MessageHelper.sendMessageToChannel(privateChannel, changeMsg);
                    GMService.postToActivityThread(game, changeMsg);
                }
                // Board changed — drop a fresh GM-view map into the activity thread.
                GMService.refreshMapInActivityThread(game);
            } else {
                String changeMsg = "**Map changes from lore:**\n"
                        + results.mapChanges().stream()
                                .map(LoreEffects.EffectDescription::text)
                                .collect(Collectors.joining("\n"));
                for (MessageChannel channel : channels) {
                    MessageHelper.sendMessageToChannel(channel, changeMsg);
                }
            }
        }
        if (!results.playerChanges().isEmpty()) {
            String changeMsg = "**Player changes from lore:**\n" + String.join("\n", results.playerChanges());
            if (game.isFowMode()) {
                // Sheet info is private in FoW: only the triggering player (private channel) and the GM
                // (activity thread) see these.
                MessageChannel privateChannel = player.getPrivateChannel();
                if (privateChannel != null) MessageHelper.sendMessageToChannel(privateChannel, changeMsg);
                GMService.postToActivityThread(game, changeMsg);
            } else {
                for (MessageChannel channel : channels) {
                    MessageHelper.sendMessageToChannel(channel, changeMsg);
                }
            }
        }
    }
}
