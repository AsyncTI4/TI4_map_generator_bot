package ti4.service.option;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.logging.BotLogger;
import ti4.service.fow.AnonymousCommsService;
import ti4.service.fow.FOWPlusService;
import ti4.service.fow.FogPreset;
import ti4.service.fow.FogPresetService;
import ti4.service.fow.GMService;

@UtilityClass
public class FOWOptionService {

    public static final int MAX_OPTIONS_PER_TAB = 9;

    private static final String OPTION_PREFIX = "fowOption_";
    private static final String TAB_PREFIX = "fowOptionCategory_";
    private static final String LOCKED_PREFIX = "fowOptionLocked_";
    private static final String PRESET_MENU = "fowPresetMenu";
    private static final String PRESET_PREVIEW_PREFIX = "fowPresetPreview_";
    private static final String PRESET_APPLY_PREFIX = "fowPresetApply_";
    private static final String FOWPLUS_MENU = "fowPlusMenu";
    private static final String FOWPLUS_SET_PREFIX = "fowPlusSet_";

    public enum FOWOptionCategory {
        // Names must stay underscore-free: changeFOWOptions splits button IDs on "_" with the
        // category as the first token, so an underscore here would break that parse.
        COMMS("Comms & Agenda"),
        VISIBILITY("Visibility"),
        PLAYERSTATS("Player stats"),
        MAP("Map display"),
        FLOW("Game flow");

        @Getter
        private final String title;

        FOWOptionCategory(String title) {
            this.title = title;
        }

        static FOWOptionCategory fromString(String string) {
            String name = switch (string.toUpperCase(Locale.ROOT)) {
                case "GAME" -> COMMS.name();
                case "OTHER" -> FLOW.name();
                default -> string.toUpperCase(Locale.ROOT);
            };
            for (FOWOptionCategory category : values()) {
                if (category.name().equals(name)) {
                    return category;
                }
            }
            throw new IllegalArgumentException("No FOWOptionCategory enum for '" + string + "'");
        }
    }

    public enum FOWOption {
        MANAGED_COMMS(FOWOptionCategory.COMMS, "Managed comms", "Bot-managed player-to-player communication threads"),
        ANONYMOUS_COMMS(
                FOWOptionCategory.COMMS,
                "Anonymous comms",
                "Bot-relayed comms that hide who plays which color; replaces Managed comms"),
        ALLOW_AGENDA_COMMS(
                FOWOptionCategory.COMMS,
                "Comms in agenda",
                "Managed comm threads let everyone talk to everyone in the Agenda Phase"),
        HIDE_TOTAL_VOTES(FOWOptionCategory.COMMS, "Hide total votes", "The agenda shows no vote total"),
        HIDE_VOTE_ORDER(FOWOptionCategory.COMMS, "Hide voting order", "The vote order shows no player colors"),

        BRIGHT_NOVAS(FOWOptionCategory.VISIBILITY, "Bright novas", "Supernovas are always visible"),
        HIDE_MAP(FOWOptionCategory.VISIBILITY, "Hide unexplored map", "Unexplored (blue 0b) map tiles are hidden"),
        HIDE_UNSEEN_HYPERLANES(
                FOWOptionCategory.VISIBILITY,
                "Hide unseen hyperlanes",
                "Custom hyperlanes show only the lanes a player's vision runs along; remembered ones show no lanes"),
        HIDE_PLAYER_INFOS(
                FOWOptionCategory.VISIBILITY,
                "Hide player infos",
                "Anchored player info areas are hidden from the map"),
        HIDE_PLAYER_NAMES(
                FOWOptionCategory.VISIBILITY, "Hide real names", "Player Discord names are hidden on the map"),
        HIDE_EXPLORES(
                FOWOptionCategory.VISIBILITY, "Hide explore decks", "Players cannot look at explore and relic decks"),
        HIDE_AC_DISCARD(
                FOWOptionCategory.VISIBILITY,
                "Hide AC discard",
                "The action card discard pile shows only cards that were played"),

        STATS_FROM_HS_ONLY(
                FOWOptionCategory.PLAYERSTATS,
                "Stats from HS only",
                "Seeing a player's home system is the only way to see their stats"),
        HIDE_STATS_VIA_FACTION_PN(
                FOWOptionCategory.PLAYERSTATS,
                "Hide stats via faction PN",
                "A faction promissory note in your play area no longer reveals that player's stats"),
        HIDE_STATS_VIA_ALLIANCE(
                FOWOptionCategory.PLAYERSTATS,
                "Hide stats via Alliance",
                "An Alliance card in your play area no longer reveals that player's stats"),
        HIDE_STATS_VIA_SFTT(
                FOWOptionCategory.PLAYERSTATS,
                "Hide stats via SftT",
                "A Support for the Throne card in your play area no longer reveals that player's stats"),
        HIDE_STATS_VIA_MAHACT_CC(
                FOWOptionCategory.PLAYERSTATS,
                "Hide stats via Mahact CC",
                "A Mahact command counter in your fleet no longer reveals that player's stats"),

        CLASSIC_MAP_LAYOUT(
                FOWOptionCategory.MAP,
                "Classic map layout",
                "Show the full classic map instead of framing it to what each player knows; map sectors are ignored"),
        FRACTURE_SEPARATE_MAP(
                FOWOptionCategory.MAP,
                "Separate Fracture map",
                "Show the Fracture as its own map segment instead of below the galaxy"),
        MAP_CONNECTIONS(
                FOWOptionCategory.MAP,
                "Map connections",
                "Under each sector map, show the known systems on other maps or sectors it connects to"),
        GHOST_HEXES(
                FOWOptionCategory.MAP,
                "Ghost hexes",
                "Faint numbered rings on unexplored hexes next to known space; players can hide them with /user fog_ghost_hexes"),

        NEW_TRANSACTIONS(
                FOWOptionCategory.FLOW,
                "New transactions",
                "Offer/accept transactions instead of the legacy direct-send flow"),
        FOG_QOL_01(
                FOWOptionCategory.FLOW,
                "Fog QoL 01",
                "Pre-decline strategy cards, auto status action cards, private reminders, GM waiting-on info"),
        GM_TURN_MAP(
                FOWOptionCategory.FLOW,
                "GM turn map",
                "Post the unfogged map to the GM activity log at the end of every turn"),
        STATUS_SUMMARY(
                FOWOptionCategory.FLOW, "Status summary", "Post explore info as a summary thread in status homework"),
        OPTIONAL_PILLAGABLE_TG(
                FOWOptionCategory.FLOW,
                "Optional pillage-able TG",
                "Ask before auto-granting TG that could trigger Pillage (Stellar Genesis/Magmus, Rear Admiral Farran)"),
        DISABLE_FRACTURE(
                FOWOptionCategory.FLOW,
                "Disable Fracture",
                "The Fracture can never enter play. Same setting as /game weird_game_setup no_fracture"),

        FOW_PLUS(
                null, "FoW+", "Harder fog: forces stricter options, its own explore deck and blind activations", false),
        RIFTSET_MODE(null, "RiftSet mode", "One-shot RiftSet setup for the fow300 scenario", false);

        private final FOWOptionCategory category;

        @Getter
        private final String title;

        private final String description;
        private final boolean visible;

        FOWOption(FOWOptionCategory category, String title, String description) {
            this(category, title, description, true);
        }

        FOWOption(FOWOptionCategory category, String title, String description, boolean visible) {
            this.category = category;
            this.title = title;
            this.description = description;
            this.visible = visible;
        }

        public FOWOptionCategory getCategory() {
            return category;
        }

        String getDescription() {
            return description;
        }

        public boolean isVisible() {
            return visible;
        }

        public static FOWOption fromString(String value) {
            for (FOWOption option : values()) {
                if (option.name().equalsIgnoreCase(value)) {
                    return option;
                }
            }
            throw new IllegalArgumentException("No FOWOption enum for '" + value + "'");
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    public static void offerFOWOptionButtons(Game game) {
        post(GMService.getGMChannel(game), optionsPanel(game, FOWOptionCategory.COMMS));
    }

    public static List<FOWOption> optionsIn(FOWOptionCategory tab) {
        return Arrays.stream(FOWOption.values())
                .filter(option -> option.isVisible() && option.getCategory() == tab)
                .toList();
    }

    public static List<MessageTopLevelComponent> optionsPanel(Game game, FOWOptionCategory tab) {
        List<ContainerChildComponent> body = new ArrayList<>();
        body.add(TextDisplay.of(panelHeader(game, tab)));
        for (FOWOption option : optionsIn(tab)) {
            body.add(optionSection(game, tab, option));
        }
        return List.of(Container.of(body), ActionRow.of(tabButtons(tab)), ActionRow.of(utilityButtons(game, tab)));
    }

    public static List<MessageTopLevelComponent> presetMenu(Game game) {
        String text = "### Fog presets\n" + FogPresetService.status(game) + "\n\n" + FogPresetService.presetList()
                + "\n\nPick a preset to see what it changes before applying it.";
        FogPreset current = FogPresetService.matching(game).orElse(null);
        List<Button> presets = Arrays.stream(FogPreset.values())
                .map(preset -> preset == current
                        ? Buttons.gray(PRESET_PREVIEW_PREFIX + preset.getId(), preset.getTitle())
                        : Buttons.blue(PRESET_PREVIEW_PREFIX + preset.getId(), preset.getTitle()))
                .toList();
        return List.of(
                Container.of(TextDisplay.of(text)),
                ActionRow.of(presets),
                ActionRow.of(Buttons.gray(TAB_PREFIX + FOWOptionCategory.COMMS, "Back to options")));
    }

    public static List<MessageTopLevelComponent> presetPreview(Game game, FogPreset preset) {
        return List.of(
                Container.of(TextDisplay.of(FogPresetService.preview(game, preset))),
                ActionRow.of(
                        Buttons.green(PRESET_APPLY_PREFIX + preset.getId(), "Apply " + preset.getTitle()),
                        Buttons.gray(PRESET_MENU, "Back to presets")));
    }

    public static List<MessageTopLevelComponent> fowPlusMenu(Game game) {
        boolean active = FOWPlusService.isActive(game);
        StringBuilder text = new StringBuilder("### FoW+ is ")
                .append(active ? "on" : "off")
                .append("\nFoW+ forces these options while it is on:\n");
        for (var forced : FOWPlusService.FORCED_FOWPLUS_OPTIONS) {
            text.append("- ")
                    .append(forced.getLeft().getTitle())
                    .append(": ")
                    .append(Boolean.TRUE.equals(forced.getRight()) ? "on" : "off")
                    .append('\n');
        }
        text.append("It also changes play:\n")
                .append(FOWPlusService.FOWPLUS_PLAY_CHANGES)
                .append("\nTurning it off keeps the forced options as they are and puts back the explore deck")
                .append(" the game had before, if the bot saved it.");
        Button toggle = active
                ? Buttons.red(FOWPLUS_SET_PREFIX + false, "Turn FoW+ off")
                : Buttons.green(FOWPLUS_SET_PREFIX + true, "Turn FoW+ on");
        return List.of(
                Container.of(TextDisplay.of(text.toString())),
                ActionRow.of(toggle, Buttons.gray(TAB_PREFIX + FOWOptionCategory.COMMS, "Back to options")));
    }

    private static String panelHeader(Game game, FOWOptionCategory tab) {
        return "### Fog options · " + tab.getTitle() + "\n" + FogPresetService.status(game) + " · FoW+ "
                + (FOWPlusService.isActive(game) ? "on" : "off");
    }

    private static Section optionSection(Game game, FOWOptionCategory tab, FOWOption option) {
        String text = "**" + option.getTitle() + "** " + valueRepresentation(isEnabled(game, option)) + "\n-# "
                + option.getDescription();
        return Section.of(accessory(game, tab, option), TextDisplay.of(text));
    }

    private static Button accessory(Game game, FOWOptionCategory tab, FOWOption option) {
        if (isLockedByFowPlus(game, option)) {
            return Buttons.gray(LOCKED_PREFIX + option, "Locked by FoW+").asDisabled();
        }
        if (option == FOWOption.STATUS_SUMMARY && FOWPlusService.isActive(game)) {
            return Buttons.gray(LOCKED_PREFIX + option, "No effect under FoW+").asDisabled();
        }
        boolean enabled = isEnabled(game, option);
        String id = OPTION_PREFIX + tab + "_" + !enabled + "_" + option;
        return enabled ? Buttons.red(id, "Turn off") : Buttons.green(id, "Turn on");
    }

    private static List<Button> tabButtons(FOWOptionCategory selected) {
        return Arrays.stream(FOWOptionCategory.values())
                .map(tab -> tab == selected
                        ? Buttons.gray(TAB_PREFIX + tab, tab.getTitle())
                        : Buttons.blue(TAB_PREFIX + tab, tab.getTitle()))
                .toList();
    }

    private static List<Button> utilityButtons(Game game, FOWOptionCategory tab) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue(PRESET_MENU, "Presets..."));
        buttons.add(Buttons.blue(FOWPLUS_MENU, "FoW+ ..."));
        if (tab == FOWOptionCategory.COMMS && game.getFowOption(FOWOption.ANONYMOUS_COMMS)) {
            buttons.add(Buttons.blue(AnonymousCommsService.SETTINGS_BUTTON, "Anonymous comms settings..."));
        }
        if (tab == FOWOptionCategory.FLOW) {
            buttons.add(Buttons.blue("gmQolSettings~MDL", "Fog QoL 01 settings..."));
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        return buttons;
    }

    private static boolean isLockedByFowPlus(Game game, FOWOption option) {
        return FOWPlusService.isActive(game)
                && FOWPlusService.FORCED_FOWPLUS_OPTIONS.stream().anyMatch(forced -> forced.getLeft() == option);
    }

    private static void post(MessageChannel channel, List<MessageTopLevelComponent> components) {
        channel.sendMessageComponents(components).useComponentsV2().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static void show(ButtonInteractionEvent event, List<MessageTopLevelComponent> components) {
        if (!event.getMessage().isUsingComponentsV2()) {
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            post(event.getMessageChannel(), components);
            return;
        }
        event.getHook()
                .editOriginalComponents(components)
                .useComponentsV2()
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler(OPTION_PREFIX)
    public static void changeFOWOptions(ButtonInteractionEvent event, Game game, String buttonID) {
        String[] parts = buttonID.substring(OPTION_PREFIX.length()).split("_", 3);
        FOWOption option = FOWOption.fromString(parts[2]);
        if (!isLockedByFowPlus(game, option)) {
            writeOption(game, option, Boolean.parseBoolean(parts[1]));
        }
        show(event, optionsPanel(game, option.getCategory()));
    }

    @ButtonHandler(TAB_PREFIX)
    public static void changeFOWOptionsCategory(ButtonInteractionEvent event, Game game, String buttonID) {
        FOWOptionCategory tab = FOWOptionCategory.fromString(buttonID.substring(TAB_PREFIX.length()));
        show(event, optionsPanel(game, tab));
    }

    @ButtonHandler(value = PRESET_MENU, save = false)
    public static void showPresetMenu(ButtonInteractionEvent event, Game game) {
        show(event, presetMenu(game));
    }

    @ButtonHandler(value = PRESET_PREVIEW_PREFIX, save = false)
    public static void showPresetPreview(ButtonInteractionEvent event, Game game, String buttonID) {
        FogPreset preset = FogPreset.fromId(buttonID.substring(PRESET_PREVIEW_PREFIX.length()));
        show(event, presetPreview(game, preset));
    }

    @ButtonHandler(PRESET_APPLY_PREFIX)
    public static void applyPreset(ButtonInteractionEvent event, Game game, String buttonID) {
        FogPreset preset = FogPreset.fromId(buttonID.substring(PRESET_APPLY_PREFIX.length()));
        FogPresetService.applyAndAnnounce(game, preset);
        show(event, optionsPanel(game, FOWOptionCategory.COMMS));
    }

    @ButtonHandler(value = FOWPLUS_MENU, save = false)
    public static void showFowPlusMenu(ButtonInteractionEvent event, Game game) {
        show(event, fowPlusMenu(game));
    }

    @ButtonHandler(FOWPLUS_SET_PREFIX)
    public static void setFowPlus(ButtonInteractionEvent event, Game game, String buttonID) {
        FOWPlusService.setActive(game, Boolean.parseBoolean(buttonID.substring(FOWPLUS_SET_PREFIX.length())));
        show(event, optionsPanel(game, FOWOptionCategory.COMMS));
    }

    public static boolean isEnabled(Game game, FOWOption option) {
        if (option == FOWOption.DISABLE_FRACTURE) return game.isNoFractureMode();
        return game.getFowOption(option);
    }

    private static void writeOption(Game game, FOWOption option, boolean value) {
        if (option == FOWOption.DISABLE_FRACTURE) {
            game.setNoFractureMode(value);
        } else {
            game.setFowOption(option, value);
        }
        if (value) disableConflictingCommsOption(game, option);
    }

    private static void disableConflictingCommsOption(Game game, FOWOption enabled) {
        if (enabled == FOWOption.ANONYMOUS_COMMS) {
            game.setFowOption(FOWOption.MANAGED_COMMS, false);
            AnonymousCommsService.offerManagedThreadCleanup(game);
        }
        if (enabled == FOWOption.MANAGED_COMMS) game.setFowOption(FOWOption.ANONYMOUS_COMMS, false);
    }

    public static String valueRepresentation(boolean value) {
        return value ? "✅" : "🚫";
    }
}
