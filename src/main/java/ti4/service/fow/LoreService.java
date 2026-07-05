package ti4.service.fow;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final int FOOTER_TEXT_MAX_LENGTH = 400;
    // TODO: editing existing lore does not re-validate RECEIVER against current game mode; an entry saved in FoW
    //       mode with ADJACENT/GM receiver remains editable without correction in non-FoW mode.
    // TODO: LORECACHE is a plain HashMap — not thread-safe under concurrent event handlers.

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
        GROUND_BATTLE("A ground battle was fought here"),
        // Phase triggers pair only with the phase pseudo-targets (strategy/action/status/agenda) —
        // see showPhaseStartLore/showPhaseEndLore; validateLore rejects any other pairing.
        PHASE_START("Phase begins"),
        PHASE_END("Phase ends");
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

        /** 0 means unbounded on that side — the entry can fire from game start / has no end round. */
        public int fromRound = 0;

        public int tillRound = 0;

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
                entry.fromRound = Integer.parseInt(splitLore[7]);
                entry.tillRound = Integer.parseInt(splitLore[8]);
            } catch (Exception e) {
                // Ignore invalid/missing fields — also covers exports written before round-gating existed,
                // which simply won't have splitLore[7]/[8] — and use defaults for whatever wasn't parsed yet.
            }
            return entry;
        }

        /** True if {@code round} falls inside this entry's from/till bounds (0 on a side = unbounded there). */
        public boolean isInRoundRange(int round) {
            return (fromRound <= 0 || round >= fromRound) && (tillRound <= 0 || round <= tillRound);
        }

        @Override
        public String toString() {
            return target + ";" + loreText + ";" + footerText + ";" + receiver + ";" + trigger + ";" + ping + ";"
                    + persistance + ";" + fromRound + ";" + tillRound;
        }

        /** A field-for-field clone. Used when saving one set of lore settings to several targets at once so each
         *  stored entry is its own instance instead of a shared, mutated reference. */
        public LoreEntry copy() {
            LoreEntry clone = new LoreEntry(loreText);
            clone.target = target;
            clone.footerText = footerText;
            clone.receiver = receiver;
            clone.trigger = trigger;
            clone.ping = ping;
            clone.persistance = persistance;
            clone.fromRound = fromRound;
            clone.tillRound = tillRound;
            return clone;
        }

        private static final String CHOICE_MARKER = "!choice";

        /** {@code !roll <count>d<sides>}, e.g. {@code !roll 2d10}. Case-insensitive, whole line. */
        private static final Pattern ROLL_MARKER = Pattern.compile("(?i)^!roll\\s+(\\d+)d(\\d+)$");

        /** A footer line whose whole body is a bare numeric range/value, e.g. {@code 2-10:} or {@code 5:} —
         *  a roll-bin tag rather than the fixed {@code accept:}/{@code reject:} choice tags. */
        private static final Pattern BIN_TAG = Pattern.compile("^(\\d+(?:-\\d+)?):\\s*(.*)$", Pattern.DOTALL);

        /**
         * A footer line that is just {@code !choice} gates the whole entry behind an Accept/Reject
         * confirmation sent individually to each recipient — see {@code LoreService#requestChoiceConfirmation}.
         */
        public boolean isChoiceGated() {
            for (String line : footerText.split("\n")) {
                if (CHOICE_MARKER.equalsIgnoreCase(line.strip())) return true;
            }
            return false;
        }

        /**
         * A footer line matching {@code !roll <count>d<sides>} gates the whole entry behind a single "Roll"
         * button sent individually to each recipient — see {@code LoreService#requestRollConfirmation}. Which
         * effect lines fire is decided by which {@code N-M:}-tagged bin the rolled total lands in.
         */
        public boolean isRollGated() {
            return getRollSpec() != null;
        }

        /** {@code {count, sides}} of this entry's {@code !roll} marker, or null if it isn't roll-gated. */
        public int[] getRollSpec() {
            for (String line : footerText.split("\n")) {
                Matcher m = ROLL_MARKER.matcher(line.strip());
                if (m.matches()) {
                    return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
                }
            }
            return null;
        }

        /**
         * Splits a footer line into an optional tag ("accept", "reject", or a numeric roll-bin range like
         * "2-10"/"5") and the remainder of the line after the tag; tag is null if untagged.
         *
         * The numeric bin tag is only recognized when {@code rollGated} — otherwise a plain flavor line that
         * happens to start with "N:" (e.g. "3: the gate opens") would have its prefix stripped, a regression
         * for lore written before roll gating existed. accept:/reject: are always recognized (they were
         * stripped regardless of choice-gating before roll gating shipped, so that behavior is preserved).
         */
        private static String[] splitTag(String line, boolean rollGated) {
            String lower = line.toLowerCase();
            if (lower.startsWith("accept:"))
                return new String[] {"accept", line.substring(7).strip()};
            if (lower.startsWith("reject:"))
                return new String[] {"reject", line.substring(7).strip()};
            if (rollGated) {
                Matcher m = BIN_TAG.matcher(line.strip());
                if (m.matches()) return new String[] {m.group(1), m.group(2)};
            }
            return new String[] {null, line};
        }

        /**
         * Footer lines beginning with '!' are machine-readable effects, not shown in the embed. A line
         * prefixed with "accept:"/"reject:" only fires when a {@code !choice} gate is resolved that way; a
         * line prefixed with a numeric range like "2-10:" only fires when a {@code !roll} gate's total lands
         * in that range. The tag is preserved on the returned line for {@link LoreEffects} to parse.
         */
        public List<String> getEffectLines() {
            List<String> out = new ArrayList<>();
            boolean rollGated = isRollGated();
            for (String line : footerText.split("\n")) {
                String stripped = line.strip();
                if (CHOICE_MARKER.equalsIgnoreCase(stripped)
                        || ROLL_MARKER.matcher(stripped).matches()) continue;

                String[] tagged = splitTag(stripped, rollGated);
                String tagPrefix = tagged[0] == null ? "" : tagged[0] + ":";
                String rest = tagged[1];

                // Support multiple effects on one line: "!tg +2 !fleet +1"
                for (String segment : rest.split("(?<=\\s)(?=!)")) {
                    String trimmed = segment.strip();
                    if (trimmed.startsWith("!")) {
                        out.add(tagPrefix + trimmed.substring(1).strip());
                    }
                }
            }
            return out;
        }

        /** Footer text with the !choice/!roll markers and effect lines removed, for display. */
        public String getDisplayFooter() {
            StringBuilder sb = new StringBuilder();
            boolean rollGated = isRollGated();
            for (String line : footerText.split("\n")) {
                String stripped = line.strip();
                if (CHOICE_MARKER.equalsIgnoreCase(stripped)
                        || ROLL_MARKER.matcher(stripped).matches()) continue;
                stripped = splitTag(stripped, rollGated)[1];

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

            String badRoundRange = findBadRoundRange(fields);
            if (badRoundRange != null) {
                errors.add(label + " (`" + fields[0].trim() + "`): " + badRoundRange);
                continue;
            }

            LoreEntry entry = LoreEntry.fromString(raw);
            String rawTarget = fields[0].trim();
            label += " (`" + rawTarget + "`)";

            String validatedTarget = validateLore(rawTarget, "", entry, validEntries, game);
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

    /**
     * {@code LoreEntry.fromString} silently defaults to unrestricted on a non-numeric or backwards
     * from/till round field, which would otherwise import the entry with the wrong round gating instead
     * of flagging the problem. Fields are only present in exports written after round-gating shipped, so
     * older exports (fields.length <= 8) are untouched — those simply import as unrestricted.
     */
    private static String findBadRoundRange(String[] fields) {
        if (fields.length <= 8) return null;
        int from;
        int till;
        try {
            from = fields[7].isBlank() ? 0 : Integer.parseInt(fields[7].trim());
            till = fields[8].isBlank() ? 0 : Integer.parseInt(fields[8].trim());
        } catch (NumberFormatException e) {
            return "invalid round range `" + fields[7] + "-" + fields[8] + "`";
        }
        if (from > 0 && till > 0 && from > till) {
            return "round range `" + from + "-" + till + "` has a from-round after its till-round";
        }
        return null;
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
            TargetKey key = splitTargetKey(target);
            String buttonLabel;
            String emoji;
            boolean isValidLore = true;

            if (PositionMapper.isTilePositionValid(key.base())) {
                // System Lore — the full stored key (base + tag, e.g. "522#MovedR4to6") is already
                // self-descriptive, so it's used as-is for the label.
                Tile tile = game.getTileByPosition(key.base());
                if (tile == null) isValidLore = false;

                buttonLabel = target;
                emoji = tile != null
                                && tile.getTileModel() != null
                                && tile.getTileModel().getEmoji() != null
                        ? tile.getTileModel().getEmoji().toString()
                        : null;
            } else if (isPhaseTarget(key.base())) {
                // Phase Lore — the stored key ("strategy", "agenda#PhaseEndR3") is self-descriptive.
                buttonLabel = target;
                emoji = null;
            } else {
                // Planet Lore — targets are stored as canonical planet IDs, but resolve aliases defensively
                // so a button still renders as valid if an entry was ever stored under an alias.
                String planetId = AliasHandler.resolvePlanet(key.base());
                PlanetModel planet = Mapper.getPlanet(planetId);
                if (!game.getPlanets().contains(planetId)) isValidLore = false;

                String label = planet == null ? key.base() : planet.getName();
                buttonLabel = key.tag() == null ? label : label + " #" + key.tag();
                emoji = planet != null && planet.getEmoji() != null
                        ? planet.getEmoji().toString()
                        : null;
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

    static final char TAG_DELIMITER = '#';

    /** A stored/typed target split into its system-or-planet base and an optional "#tag" disambiguator.
     *  Package-private: {@link LoreEffects} needs the same split to resolve a tagged entry's tile. */
    record TargetKey(String base, String tag) {}

    static TargetKey splitTargetKey(String storedOrTyped) {
        if (storedOrTyped == null) return new TargetKey(null, null);
        int idx = storedOrTyped.indexOf(TAG_DELIMITER);
        if (idx < 0) return new TargetKey(storedOrTyped, null);
        return new TargetKey(storedOrTyped.substring(0, idx), storedOrTyped.substring(idx + 1));
    }

    /** Resolves the board position for a (possibly "#tag"-suffixed) lore target: itself for system lore,
     *  or the position of the system containing the underlying planet for planet lore. */
    private static String resolvePosition(Game game, String target, boolean isSystemLore) {
        if (isSystemLore) return target;
        Tile tile = game.getTileFromPlanet(splitTargetKey(target).base());
        return tile != null ? tile.getPosition() : target;
    }

    public static Map<String, LoreEntry> getGameLore(Game game) {
        if (!LORECACHE.containsKey(game.getName())) {
            LORECACHE.put(game.getName(), readLore(game.getStoredValue(SYSTEM_LORE_KEY)));
        }
        return LORECACHE.get(game.getName());
    }

    public static void evictGameLore(String gameName) {
        LORECACHE.remove(gameName);
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
        TextInput.Builder rounds = TextInput.create("rounds", TextInputStyle.SHORT)
                .setRequired(false)
                .setPlaceholder("e.g. 3-6, 4, or blank for any round");

        String selections = target.replace("NEW_", "");
        String originalTarget = "";
        if (!target.startsWith("NEW")) {
            LoreEntry entry = getGameLore(game).get(target);
            position.setValue(entry.target);
            lore.setValue(entry.loreText);
            if (!StringUtils.isBlank(entry.footerText)) {
                footer.setValue(entry.footerText);
            }
            String roundRange = formatRoundRange(entry);
            if (!roundRange.isEmpty()) {
                rounds.setValue(roundRange);
            }
            originalTarget = entry.target;
            selections = entry.receiver + ":" + entry.trigger + ":" + entry.ping + ":" + entry.persistance;
        }

        // originalTarget (may be blank for a brand-new entry) rides along as the modal ID's first field so
        // saveLoreFromModal can tell "typing the same target back" (an in-place edit) apart from "typing a
        // target that collides with someone else's entry" (which should auto-tag instead of clobbering it).
        Modal editLoreModal = Modal.create("gmLoreSave_" + originalTarget + ":" + selections, "Add Lore")
                .addComponents(
                        Label.of("Target", position.build()),
                        Label.of("Lore (clear to delete)", lore.build()),
                        Label.of("Other info", footer.build()),
                        Label.of("Rounds (optional)", rounds.build()))
                .build();
        event.replyModal(editLoreModal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    /** Renders an entry's round bounds back into the "N-M" / "N" / "N-" / "-M" shorthand the modal accepts. */
    private static String formatRoundRange(LoreEntry entry) {
        if (entry.fromRound <= 0 && entry.tillRound <= 0) return "";
        if (entry.fromRound == entry.tillRound) return String.valueOf(entry.fromRound);
        return (entry.fromRound > 0 ? entry.fromRound : "") + "-" + (entry.tillRound > 0 ? entry.tillRound : "");
    }

    /**
     * Parses the "Rounds" modal field ("N", "N-M", "N-", "-M", or blank) onto {@code entry}. Blank means
     * unrestricted. On anything unparseable, or a from-round after its till-round, leaves the entry
     * unrestricted and returns a warning describing the problem; returns null when the input was valid.
     */
    private static String applyRoundRange(LoreEntry entry, String rawRounds) {
        entry.fromRound = 0;
        entry.tillRound = 0;
        String s = rawRounds == null ? "" : rawRounds.strip();
        if (s.isEmpty()) return null;

        try {
            if (s.contains("-")) {
                String[] parts = s.split("-", 2);
                entry.fromRound = parts[0].isBlank() ? 0 : Integer.parseInt(parts[0].strip());
                entry.tillRound = parts[1].isBlank() ? 0 : Integer.parseInt(parts[1].strip());
            } else {
                int round = Integer.parseInt(s);
                entry.fromRound = round;
                entry.tillRound = round;
            }
        } catch (NumberFormatException e) {
            entry.fromRound = 0;
            entry.tillRound = 0;
            return "rounds `" + rawRounds
                    + "` isn't a valid round or range (e.g. `3-6`, `4`, or blank) — ignored, entry is unrestricted";
        }
        if (entry.fromRound > 0 && entry.tillRound > 0 && entry.fromRound > entry.tillRound) {
            entry.fromRound = 0;
            entry.tillRound = 0;
            return "rounds `" + rawRounds + "` has a from-round after its till-round — ignored, entry is unrestricted";
        }
        return null;
    }

    @ModalHandler("gmLoreSave_")
    public static void saveLoreFromModal(ModalInteractionEvent event, Game game) {
        String[] targets = event.getValue(Constants.POSITION).getAsString().split(",");
        String[] modalIdParts = event.getModalId().replace("gmLoreSave_", "").split(":", 5);
        String originalTarget = modalIdParts[0];
        String[] selectedOptions = Arrays.copyOfRange(modalIdParts, 1, modalIdParts.length);
        Map<String, LoreEntry> loreMap = getGameLore(game);

        LoreEntry newEntry =
                new LoreEntry(clean(event.getValue(Constants.MESSAGE).getAsString()));
        newEntry.footerText = clean(event.getValue("footer").getAsString());
        newEntry.receiver = RECEIVER.valueOf(selectedOptions[0]);
        newEntry.trigger = TRIGGER.valueOf(selectedOptions[1]);
        newEntry.ping = PING.valueOf(selectedOptions[2]);
        newEntry.persistance = PERSISTANCE.valueOf(selectedOptions[3]);
        String roundRangeProblem =
                applyRoundRange(newEntry, event.getValue("rounds").getAsString());

        // Validate
        List<String> validTargets = new ArrayList<>();
        for (String s : targets) {
            String validatedTarget = validateLore(s, originalTarget, newEntry, loreMap, game);
            if (validatedTarget == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), s + " is invalid to save lore");
                continue;
            }
            validTargets.add(validatedTarget);
        }

        // Construct — each target gets its own LoreEntry instance; a shared reference would leave every
        // stored key pointing at the same object with whichever target was set last.
        StringBuilder sb = new StringBuilder();
        for (String target : validTargets) {
            LoreEntry entryForTarget = newEntry.copy();
            entryForTarget.target = target;
            setLore(entryForTarget, game, sb);
        }

        if (!validTargets.isEmpty()) {
            saveLore(game);
        }

        if (roundRangeProblem != null && !validTargets.isEmpty()) {
            sb.append("\n⚠️ ").append(roundRangeProblem);
        }

        // validateEffects needs a target on the entry for target-dependent checks (phase-entry warnings);
        // newEntry itself never got one — only its stored copies did — so borrow the first valid target.
        if (!validTargets.isEmpty()) {
            newEntry.target = validTargets.getFirst();
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

    /**
     * Package-private for tests.
     *
     * @param originalTarget the exact key this save was opened for ("" for a brand-new entry). Typing that
     *                        same key back is always an in-place edit; typing anything else that collides
     *                        with a different, already-existing entry auto-tags instead of clobbering it.
     */
    static String validateLore(
            String rawTarget,
            String originalTarget,
            LoreEntry loreEntry,
            Map<String, LoreEntry> savedLoreMap,
            Game game) {
        String target = rawTarget.trim();
        if (savedLoreMap.containsKey(target) && StringUtils.isBlank(loreEntry.loreText)) {
            return target; // Deleting existing lore (by its exact stored key) is always valid, even if stale
        }

        if (loreEntry.loreText.length() > LORE_TEXT_MAX_LENGTH
                || loreEntry.footerText.length() > FOOTER_TEXT_MAX_LENGTH) {
            return null; // Too long
        }

        TargetKey typed = splitTargetKey(target);
        if (typed.tag() != null
                && (typed.tag().isEmpty() || !typed.tag().chars().allMatch(Character::isLetterOrDigit))) {
            return null; // tag must be non-empty and letters/digits only
        }

        String canonicalBase;
        if (PositionMapper.isTilePositionValid(typed.base())) {
            if (game.getTileByPosition(typed.base()) == null) {
                return null;
            }
            canonicalBase = typed.base();
        } else if (isPhaseTarget(typed.base())) {
            // Phase pseudo-target — checked before planet resolution so no planet alias can shadow it.
            canonicalBase = typed.base().toLowerCase();
        } else {
            PlanetModel planet =
                    Mapper.getPlanet(AliasHandler.resolvePlanet(typed.base().replace(" ", "")));
            if (planet == null || !game.getPlanets().contains(planet.getID())) {
                return null;
            }
            canonicalBase = planet.getID();
        }

        // Phase targets pair only with phase triggers and vice versa — a mismatched entry could never
        // fire (trigger scans match on base AND trigger), so refuse to save the junk state.
        boolean phaseTrigger = loreEntry.trigger == TRIGGER.PHASE_START || loreEntry.trigger == TRIGGER.PHASE_END;
        if (isPhaseTarget(canonicalBase) != phaseTrigger) {
            return null;
        }

        if (typed.tag() != null) {
            return canonicalBase + TAG_DELIMITER + typed.tag(); // an explicit tag is respected even if it collides
        }

        if (canonicalBase.equals(originalTarget) || !savedLoreMap.containsKey(canonicalBase)) {
            return canonicalBase; // in-place edit of the same entry, or the bare key is free
        }

        // A bare target collided with a different, already-existing entry — auto-tag instead of clobbering it.
        return autoTag(canonicalBase, loreEntry, savedLoreMap);
    }

    /**
     * Builds a free {@code base#TriggerRoundinfo[N]} key for a bare target that collided with someone
     * else's entry, so the new entry doesn't silently overwrite it. The tag is derived from the new
     * entry's trigger and round range so the resulting button reads as self-descriptive without opening it.
     * Package-private for tests.
     */
    static String autoTag(String base, LoreEntry loreEntry, Map<String, LoreEntry> savedLoreMap) {
        String descriptor = pascalCase(loreEntry.trigger.name()) + roundSuffix(loreEntry);
        String candidate = base + TAG_DELIMITER + descriptor;
        for (int n = 2; savedLoreMap.containsKey(candidate); n++) {
            candidate = base + TAG_DELIMITER + descriptor + n;
        }
        return candidate;
    }

    /** "SPACE_BATTLE" -> "SpaceBattle". Used to build a readable, letters-only auto-tag segment. */
    private static String pascalCase(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String part : enumName.split("_")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /** Renders an entry's round bounds as a short letters+digits tag segment, e.g. "R4to6", "R4plus", "". */
    private static String roundSuffix(LoreEntry entry) {
        if (entry.fromRound <= 0 && entry.tillRound <= 0) return "";
        if (entry.fromRound > 0 && entry.fromRound == entry.tillRound) return "R" + entry.fromRound;
        if (entry.fromRound > 0 && entry.tillRound > 0) return "R" + entry.fromRound + "to" + entry.tillRound;
        if (entry.fromRound > 0) return "R" + entry.fromRound + "plus";
        return "Rupto" + entry.tillRound;
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
        // Player-facing content always uses the base system/planet — a GM's "#tag" disambiguator is
        // storage/bookkeeping only and should never leak into what a player actually sees.
        String base = splitTargetKey(target).base();
        Tile tile = isSystemLore ? game.getTileByPosition(base) : game.getTileFromPlanet(base);
        PlanetModel planet = isSystemLore ? null : Mapper.getPlanet(base);
        String titleTile = "of ";
        if (isPhaseTarget(base)) {
            // Phases are public knowledge, so the title names the phase even for RECEIVER.ALL —
            // the anonymous random title exists to hide *where* lore was found, which doesn't apply.
            titleTile = "of the " + StringUtils.capitalize(base) + " Phase";
        } else if (lore.receiver == RECEIVER.ALL) {
            titleTile = RandomHelper.pickRandomFromList(UNKNOWN_LORE_TARGETS);
        } else if (isSystemLore && tile != null && tile.getTileModel() != null) {
            titleTile += base + " - " + tile.getTileModel().getNameNullSafe() + " "
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

    // Games created before this gate shipped keep working as-is (grandfathered by creation date),
    // since /special2 lore was already usable unrestricted in non-FoW games before lore_mode existed.
    // Any game created after this cutoff is a fresh game and must opt in via /game weird-game-setup.
    private static final long LORE_MODE_GATE_CUTOFF_MILLIS =
            Instant.parse("2026-06-20T00:00:00Z").toEpochMilli();

    // FoW games always have lore available. Non-FoW games created after the cutoff need lore_mode
    // explicitly enabled via /game weird-game-setup.
    public static boolean isLoreEnabled(Game game) {
        return game.isFowMode() || game.isLoreMode();
    }

    /**
     * Every stored entry whose target shares this base (ignoring any "#tag" disambiguator), matches the
     * trigger, and is inside its round window. A bare position/planet can now have more than one entry
     * tagged onto it, so trigger call sites — which only ever know the bare base, never a tag — look up
     * "everything live here" instead of a single exact key, and {@link #showSystemLore}/{@link
     * #showPlanetLore} delivers each match independently.
     */
    private static List<LoreEntry> matchingEntries(Game game, String base, TRIGGER trigger) {
        if (!isLoreEnabled(game)) return List.of();
        int round = game.getRound();
        List<LoreEntry> matches = new ArrayList<>();
        for (LoreEntry entry : getGameLore(game).values()) {
            if (entry.trigger == trigger
                    && entry.isInRoundRange(round)
                    && splitTargetKey(entry.target).base().equals(base)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    // -------------------------------------------------------------------------------------------------
    // Phase-triggered lore: entries whose target is a phase pseudo-target (strategy/action/status/agenda)
    // and whose trigger is PHASE_START/PHASE_END fire on phase transitions, not player actions.
    // -------------------------------------------------------------------------------------------------

    /** The four phase pseudo-targets. Checked in validateLore BEFORE planet resolution, so no planet
     *  alias can ever shadow them (verified none collides today). */
    private static final Set<String> PHASE_TARGETS = Set.of("strategy", "action", "status", "agenda");

    static boolean isPhaseTarget(String base) {
        return base != null && PHASE_TARGETS.contains(base.toLowerCase());
    }

    /**
     * Maps the free-form {@code game.getPhaseOfGame()} strings onto the four phase pseudo-targets:
     * sub-states collapse ("statusScoring"/"statusHomework" → status, "agendaVoting"/"agendawaiting"/
     * "agendaEnd" → agenda); setup/draft states ("miltydraft", "playerSetup", blank) return null so no
     * phase lore ever fires for them.
     */
    private static String normalizePhase(String rawPhase) {
        if (rawPhase == null) return null;
        String lower = rawPhase.toLowerCase();
        if (lower.startsWith("status")) return "status";
        if (lower.startsWith("agenda")) return "agenda";
        if ("strategy".equals(lower) || "action".equals(lower)) return lower;
        return null;
    }

    /**
     * Fires PHASE_END lore for the phase the game is currently in, then PHASE_START lore for
     * {@code newPhase}. Call from a phase-start hook BEFORE {@code setPhaseOfGame} overwrites the
     * previous phase. A re-assertion (the game is already in {@code newPhase}, e.g. a double-clicked
     * phase button re-entering the start method) fires nothing.
     *
     * Not used by the strategy-phase hook: the round number increments during strategy start, so its
     * ends and start must fire on opposite sides of the increment — it calls {@link #showPhaseEndLore}
     * and {@link #showPhaseStartLore} separately instead.
     */
    public static void showPhaseLore(Game game, String newPhase) {
        String prev = normalizePhase(game.getPhaseOfGame());
        String next = normalizePhase(newPhase);
        if (next != null && next.equals(prev)) return; // re-assertion, not a transition — fire nothing
        showPhaseEndLore(game, newPhase);
        showPhaseStartLore(game, newPhase);
    }

    /**
     * Fires PHASE_END lore for the game's current phase. {@code phaseAboutToStart} guards re-entry: if
     * the current phase already normalizes to it, this is a re-assertion, not a transition, and nothing
     * fires. Call before any round-number mutation so "end of round N" round gates see the round they close.
     */
    public static void showPhaseEndLore(Game game, String phaseAboutToStart) {
        if (!isLoreEnabled(game)) return;
        String prev = normalizePhase(game.getPhaseOfGame());
        if (prev == null || prev.equals(normalizePhase(phaseAboutToStart))) return;
        for (LoreEntry entry : matchingEntries(game, prev, TRIGGER.PHASE_END)) {
            showPhaseLoreEntry(game, entry);
        }
    }

    /** Fires PHASE_START lore for {@code phase}, at most once per phase per round (see below). */
    public static void showPhaseStartLore(Game game, String phase) {
        if (!isLoreEnabled(game)) return;
        String next = normalizePhase(phase);
        if (next == null) return;
        List<LoreEntry> matches = matchingEntries(game, next, TRIGGER.PHASE_START);
        if (matches.isEmpty() || phaseStartAlreadyFiredThisRound(game, next)) return;
        for (LoreEntry entry : matches) {
            showPhaseLoreEntry(game, entry);
        }
    }

    /**
     * Round-scoped dedup for PHASE_START: the strategy-start hook can't use the re-assertion guard
     * (by the time its START fires, {@code phaseOfGame} is already "strategy" on both a first and a
     * repeated call), so a stored value records which round each phase's start lore last fired in.
     * Only written when entries actually matched, to avoid bookkeeping in games not using phase lore.
     */
    private static boolean phaseStartAlreadyFiredThisRound(Game game, String phase) {
        String key = "lorePhaseStartFired_" + phase;
        String round = String.valueOf(game.getRound());
        if (round.equals(game.getStoredValue(key))) return true;
        game.setStoredValue(key, round);
        return false;
    }

    /**
     * Routes one matched phase entry: {@code !choice}/{@code !roll} gates get their per-player buttons
     * (audience only meaningful for RECEIVER.ALL — the placeholder "triggering player" argument is
     * ignored for that receiver); everything else delivers immediately.
     */
    private static void showPhaseLoreEntry(Game game, LoreEntry loreEntry) {
        if ((loreEntry.isChoiceGated() || loreEntry.isRollGated()) && loreEntry.receiver == RECEIVER.ALL) {
            List<Player> audience =
                    game.getRealPlayers().stream().filter(p -> !p.isNpc()).toList();
            if (!audience.isEmpty()) {
                if (loreEntry.isChoiceGated()) {
                    requestChoiceConfirmation(
                            audience.getFirst(), game, loreEntry.target, true, loreEntry, loreEntry.target);
                } else {
                    requestRollConfirmation(
                            audience.getFirst(), game, loreEntry.target, true, loreEntry, loreEntry.target);
                }
                return;
            }
        }
        deliverPhaseLore(game, loreEntry);
    }

    /**
     * Playerless delivery for phase lore. Receiver ALL (and everything that isn't GM — validation warns)
     * announces in the main game channel; GM announces in the GM channel. Map-mutating effect lines fire
     * exactly once; player-stat lines fan out to every real player so per-player {@code ?conditions} gate
     * individually. GM receiver grants no player rewards, matching deliverLore's rule.
     */
    static void deliverPhaseLore(Game game, LoreEntry loreEntry) {
        String target = loreEntry.target;

        MessageChannel channel;
        String ping;
        if (loreEntry.receiver == RECEIVER.GM) {
            // getGMChannel searches the guild's channels by name — guard for guildless (test) games.
            channel = game.getGuild() != null ? GMService.getGMChannel(game) : null;
            ping = GMService.gmPing(game);
        } else {
            channel = game.getMainGameChannel();
            ping = game.getPing();
        }

        MessageEmbed embed = buildLoreEmbed(game, target, loreEntry, true);
        if (channel != null) {
            MessageHelper.sendMessageToChannelWithEmbed(channel, ping + ", Lore was discovered.", embed);
        }

        // Map effects need *a* player as context, but validation requires phase entries to spell out
        // colors and @targets explicitly, so which player carries the context never matters. Prefer the
        // neutral (Dicecord) player so a forgotten explicit color mutates neutral units, not a random
        // player's.
        Player contextPlayer = game.getPlayer(Constants.dicecordId);
        if (contextPlayer == null && !game.getRealPlayers().isEmpty()) {
            contextPlayer = game.getRealPlayers().getFirst();
        }
        Set<MessageChannel> channels = channel != null ? Set.of(channel) : Set.of();
        if (contextPlayer != null) {
            LoreEffects.EffectResults mapResults =
                    LoreEffects.applyLoreEffectsMapOnly(contextPlayer, game, loreEntry, true, null);
            reportEffectResults(contextPlayer, game, mapResults, target, channels);
        }

        if (loreEntry.receiver != RECEIVER.GM) {
            for (Player p : game.getRealPlayers()) {
                LoreEffects.EffectResults results =
                        LoreEffects.applyLoreEffectsPlayerOnly(p, game, loreEntry, true, null);
                MessageChannel playerChannel = p.getCorrectChannel();
                reportEffectResults(p, game, results, target, playerChannel != null ? Set.of(playerChannel) : Set.of());
            }
        }

        GMService.logActivity(game, "Phase lore fired for " + target, loreEntry.ping == PING.YES);

        // ONCE_PER_PLAYER has no meaning without a triggering player (validation warns) — treat as ONCE.
        if (loreEntry.persistance != PERSISTANCE.ALWAYS) {
            getGameLore(game).remove(target);
            saveLore(game);
        }
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
        List<LoreEntry> matches = matchingEntries(game, position, trigger);
        if (matches.isEmpty()) return;

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

        for (LoreEntry entry : matches) {
            showLore(player, game, entry.target, true);
        }
    }

    public static void showPlanetLore(Player player, Game game, String planet, TRIGGER trigger) {
        List<LoreEntry> matches = matchingEntries(game, planet, trigger);
        if (matches.isEmpty()) return;

        // CONTROLLED requires planet control; MOVED requires units on planet; GROUND_BATTLE has no extra guard
        if (trigger == TRIGGER.CONTROLLED && !player.getPlanets().contains(planet)
                || trigger == TRIGGER.MOVED
                        && !FoWHelper.playerHasUnitsOnPlanet(player, game.getTileFromPlanet(planet), planet)) {
            return;
        }

        for (LoreEntry entry : matches) {
            showLore(player, game, entry.target, false);
        }
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

        String position = resolvePosition(game, target, isSystemLore);

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

        if (loreEntry.isRollGated()) {
            requestRollConfirmation(player, game, target, isSystemLore, loreEntry, position);
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
        // ONCE_PER_PLAYER is tracked in loreDeliveredKey; showLore's check only covers the triggering player,
        // so for ADJACENT/ALL audiences we must skip anyone who already received it here too.
        Set<String> alreadyDelivered = loreEntry.persistance == PERSISTANCE.ONCE_PER_PLAYER
                ? getStoredIdSet(game, loreDeliveredKey(target))
                : Set.of();
        for (Player p : audience) {
            if (!p.isRealPlayer() || alreadyOffered.contains(p.getUserID()) || alreadyDelivered.contains(p.getUserID()))
                continue;
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

        String position = resolvePosition(game, target, isSystemLore);
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
     * choice be offered again — fresh — the next time the entry's trigger condition fires for any player.
     * {@code ONCE_PER_PLAYER} keeps the entry but skips already-delivered players when re-offering (see
     * {@code requestChoiceConfirmation}); those players are also treated as resolved here so they don't
     * block the round from completing.
     */
    static void maybeCompleteChoiceRound(
            Player resolver, Game game, LoreEntry loreEntry, String target, String position) {
        List<Player> currentAudience = computeChoiceAudience(game, loreEntry, resolver, position);
        Set<String> resolved = getStoredIdSet(game, choiceResolvedKey(target));
        // For ONCE_PER_PLAYER, audience members who already received the lore in an earlier round are never
        // re-offered (see requestChoiceConfirmation), so they shouldn't block this round from completing.
        Set<String> alreadyDelivered = loreEntry.persistance == PERSISTANCE.ONCE_PER_PLAYER
                ? getStoredIdSet(game, loreDeliveredKey(target))
                : Set.of();
        boolean allResolved = !currentAudience.isEmpty()
                && currentAudience.stream()
                        .filter(Player::isRealPlayer)
                        .allMatch(p -> resolved.contains(p.getUserID()) || alreadyDelivered.contains(p.getUserID()));
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

    /** Sends each not-yet-offered recipient their own "Roll NdM" button in their own channel. Reuses the
     *  same offered/delivered bookkeeping as the choice flow — an entry is only ever choice-gated or
     *  roll-gated, never both, so there's no risk of the two flows colliding on the same keys. */
    static void requestRollConfirmation(
            Player triggeringPlayer,
            Game game,
            String target,
            boolean isSystemLore,
            LoreEntry loreEntry,
            String position) {
        List<Player> audience = computeChoiceAudience(game, loreEntry, triggeringPlayer, position);
        if (audience.isEmpty()) {
            // No player audience to offer the roll to (e.g. RECEIVER.GM) — fall back to unconditional delivery.
            deliverLore(triggeringPlayer, game, target, isSystemLore, loreEntry, position);
            return;
        }

        int[] spec = loreEntry.getRollSpec();
        String scope = isSystemLore ? "sys" : "planet";
        MessageEmbed embed = buildLoreEmbed(game, target, loreEntry, isSystemLore);
        Set<String> alreadyOffered = getStoredIdSet(game, choiceOfferedKey(target));
        Set<String> alreadyDelivered = loreEntry.persistance == PERSISTANCE.ONCE_PER_PLAYER
                ? getStoredIdSet(game, loreDeliveredKey(target))
                : Set.of();
        for (Player p : audience) {
            if (!p.isRealPlayer() || alreadyOffered.contains(p.getUserID()) || alreadyDelivered.contains(p.getUserID()))
                continue;
            String rollId = "loreRoll_" + scope + "_" + p.getUserID() + "_" + target;
            List<Button> buttons = List.of(Buttons.blue(rollId, "🎲 Roll " + spec[0] + "d" + spec[1]));
            MessageHelper.sendMessageToChannelWithEmbed(
                    p.getCorrectChannel(), p.getRepresentationUnfogged() + ", Lore was discovered.", embed);
            MessageHelper.sendMessageToChannel(
                    p.getCorrectChannel(), p.getRepresentation() + ", roll the dice to see your reward!", buttons);
            addStoredId(game, choiceOfferedKey(target), p.getUserID());
        }
    }

    @ButtonHandler("loreRoll_")
    private static void handleRollButton(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        ButtonHelper.deleteMessage(event);

        String[] parts = buttonID.split("_", 4);
        boolean isSystemLore = "sys".equals(parts[1]);
        String resolverUserID = parts[2];
        String target = parts[3];

        if (player == null || !resolverUserID.equals(player.getUserID())) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "This roll isn't yours to make.");
            return;
        }

        LoreEntry loreEntry = getGameLore(game).get(target);
        if (loreEntry == null) return;

        addStoredId(game, choiceResolvedKey(target), player.getUserID());

        int[] spec = loreEntry.getRollSpec();
        int total = rollDice(spec[0], spec[1]);
        String branch = LoreEffects.resolveRollBranch(loreEntry.getEffectLines(), total);

        String position = resolvePosition(game, target, isSystemLore);
        deliverRollLore(player, game, target, isSystemLore, loreEntry, position, total, branch);
    }

    private static int rollDice(int count, int sides) {
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += ThreadLocalRandom.current().nextInt(1, sides + 1);
        }
        return total;
    }

    /** Delivers (or withholds) lore + its tagged effects to a single resolver based on which bin their roll
     *  total landed in ({@code branch}, or null if it landed in none of the entry's bins — no reward). */
    static void deliverRollLore(
            Player resolver,
            Game game,
            String target,
            boolean isSystemLore,
            LoreEntry loreEntry,
            String position,
            int total,
            String branch) {
        MessageChannel channel = resolver.getCorrectChannel();
        if (branch == null) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    resolver.getRepresentationUnfoggedNoPing() + " rolled a " + total + " — no reward this time.");
        } else {
            MessageEmbed embed = buildLoreEmbed(game, target, loreEntry, isSystemLore);
            MessageHelper.sendMessageToChannelWithEmbed(
                    channel, resolver.getRepresentationUnfogged() + " rolled a " + total + " and struck lore!", embed);
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
                resolver.getRepresentationUnfoggedNoPing() + " rolled " + total + " for a lore roll at " + target,
                null,
                loreEntry.ping == PING.YES);

        if (loreEntry.persistance == PERSISTANCE.ONCE_PER_PLAYER) {
            addStoredId(game, loreDeliveredKey(target), resolver.getUserID());
        }
        maybeCompleteChoiceRound(resolver, game, loreEntry, target, position);
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

        String position = resolvePosition(game, target, isSystemLore);
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
                // The triggering player is sent directly too, but only for changes to their own color's
                // units (or effects with no color, like token/swap) — pingSystem already covers them for
                // everything else, and only if they actually have visibility on the system. Otherwise a
                // foreign-color unit change (e.g. neutral units spawned somewhere unseen) would leak straight
                // to the triggering player regardless of fog of war.
                MessageChannel privateChannel = player.getPrivateChannel();
                for (LoreEffects.EffectDescription desc : results.mapChanges()) {
                    String changeMsg = "**Map change from lore:** " + desc.text();
                    String pingPosition = desc.tilePosition() != null ? desc.tilePosition() : position;
                    FoWHelper.pingSystem(game, pingPosition, changeMsg, false);
                    boolean tellTriggeringPlayer =
                            desc.unitColor() == null || desc.unitColor().equalsIgnoreCase(player.getColor());
                    if (tellTriggeringPlayer && privateChannel != null) {
                        MessageHelper.sendMessageToChannel(privateChannel, changeMsg);
                    }
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
