package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.franken.ban.BanService;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.draft.FrankenDraft;
import ti4.game.Game;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.ListSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.service.franken.FrankenBanList;
import ti4.service.franken.FrankenDraftMode;
import ti4.service.franken.FrankenDraftStartService;
import tools.jackson.databind.JsonNode;

@Getter
public class FrankenSettings extends SettingsMenu {

    private static final String MENU_ID = "franken";
    private static final String STANDARD_DRAFT = "standard";

    private final ChoiceSetting<String> draftMode;
    private final BooleanSetting force;
    private final ListSetting<FactionModel> bannedFactions;
    private final ListSetting<FrankenBanList> banLists;

    @JsonIgnore
    private final Game game;

    public FrankenSettings(@NotNull Game game, JsonNode json) {
        super(MENU_ID, "Franken Settings", "Edit franken draft settings, then start the draft!", null);
        this.game = game;

        draftMode = new ChoiceSetting<>("DraftMode", "Draft Mode", STANDARD_DRAFT);
        draftMode.setAllValues(draftModeOptions());
        draftMode.setShow(FrankenSettings::draftModeLabel);

        force = new BooleanSetting("Force", "Force overwrite existing player setups", false);

        Set<String> empty = new HashSet<>();
        bannedFactions =
                new ListSetting<>(
                        "BannedFactions",
                        "Banned Factions",
                        "Ban faction",
                        "Unban faction",
                        legalFactionOptions(game),
                        empty,
                        empty) {
                    @Override
                    protected String shortValue() {
                        return effectiveBannedFactionSummary();
                    }

                    @Override
                    protected String longValue() {
                        return effectiveBannedFactionSummary();
                    }
                };
        bannedFactions.setGetEmoji(FactionModel::getFactionEmoji);
        bannedFactions.setShow(FactionModel::getFactionName);

        banLists = new ListSetting<>(
                "BanLists", "Ban Lists", "Use ban list", "Remove ban list", banListOptions(), empty, empty);
        banLists.setShow(FrankenBanList::getAutoCompleteName);

        if (isMenuJson(json, MENU_ID)) {
            draftMode.initialize(json.get("draftMode"));
            force.initialize(json.get("force"));
            bannedFactions.initialize(json.get("bannedFactions"));
            banLists.initialize(json.get("banLists"));
        }

        if (json != null && json.has("messageId")) {
            setMessageId(json.get("messageId").asText(null));
        }
    }

    @Override
    protected List<SettingInterface> settings() {
        return List.of(draftMode, force, bannedFactions, banLists);
    }

    @Override
    protected List<SettingsMenu> categories() {
        return List.of();
    }

    @Override
    protected List<Button> specialButtons() {
        String prefix = menuAction + "_" + navId() + "_";
        return new ArrayList<>(List.of(Buttons.green(prefix + "startFranken", "Start Franken Draft!")));
    }

    @Override
    protected String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error =
                switch (action) {
                    case "startFranken" -> startDraft(event);
                    default -> null;
                };
        return error == null ? "success" : error;
    }

    @Override
    public String menuSummaryString(String lastSettingTouched) {
        return super.menuSummaryString(lastSettingTouched) + frankenNotes();
    }

    private String startDraft(GenericInteractionCreateEvent event) {
        String validationError = FrankenDraftStartService.validateStartFrankenDraft(game, force.isVal());
        if (validationError != null) {
            return validationError;
        }
        applyBanSettings();
        return FrankenDraftStartService.startFrankenDraft(event, game, force.isVal(), selectedDraftMode());
    }

    private void applyBanSettings() {
        BanService banService = new BanService();
        for (String faction : bannedFactions.getKeys()) {
            banService.applyOption(game, Constants.BAN_FACTION, faction);
        }
        for (String banList : banLists.getKeys()) {
            banService.applyBanList(game, FrankenBanList.fromString(banList));
        }
    }

    private FrankenDraftMode selectedDraftMode() {
        String selected = draftMode.getValue();
        if (STANDARD_DRAFT.equals(selected)) {
            return null;
        }
        return FrankenDraftMode.fromString(selected);
    }

    private String effectiveBannedFactionSummary() {
        Map<String, Set<String>> banListSources = selectedBanListSources(Constants.BAN_FACTION);
        Set<String> effectiveBans = new HashSet<>(bannedFactions.getKeys());
        effectiveBans.addAll(banListSources.keySet());
        List<String> values = effectiveBans.stream()
                .map(key -> factionBanLabel(key, banListSources.getOrDefault(key, Set.of())))
                .sorted()
                .toList();
        return "[" + String.join(",", values) + "]";
    }

    private static String frankenNotes() {
        return """


            **Notes:**
            > Use the game options and homebrew settings above to enable homebrew. DS auto includes BR, if you want to play without them, add the BR ban list.

            > Due to discord message limits, component bans must be handled manually via slash command. The ban list options have popular component bans.""";
    }

    Map<String, Set<String>> selectedBanListSources(String banType) {
        Map<String, Set<String>> sources = new LinkedHashMap<>();
        for (FrankenBanList banList : selectedBanLists()) {
            for (String id : banList.getBansForType(banType)) {
                sources.computeIfAbsent(id, key -> new LinkedHashSet<>()).add(banList.getName());
            }
        }
        return sources;
    }

    List<FrankenBanList> selectedBanLists() {
        return banLists.getKeys().stream()
                .map(FrankenBanList::fromString)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String factionBanLabel(String id, Set<String> sources) {
        return withSourceLabel(factionName(id), sources);
    }

    static String factionName(String id) {
        FactionModel faction = Mapper.getFaction(id);
        return faction == null ? id : faction.getFactionName();
    }

    static String withSourceLabel(String label, Set<String> sources) {
        if (sources == null || sources.isEmpty()) {
            return label;
        }
        return label + " _(from " + String.join(", ", sources.stream().sorted().toList()) + ")_";
    }

    static boolean isMenuJson(JsonNode json, String menuId) {
        return json != null
                && json.has("menuId")
                && menuId.equals(json.get("menuId").asString(""));
    }

    public static boolean isFrankenMenuComponent(String componentId) {
        return isFrankenMenuComponent(componentId, "jmfA_franken")
                || isFrankenMenuComponent(componentId, "jmfN_franken");
    }

    private static boolean isFrankenMenuComponent(String componentId, String prefix) {
        return componentId != null
                && (componentId.equals(prefix)
                        || componentId.startsWith(prefix + "_")
                        || componentId.startsWith(prefix + "."));
    }

    private static Map<String, String> draftModeOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(STANDARD_DRAFT, STANDARD_DRAFT);
        for (FrankenDraftMode mode : FrankenDraftMode.values()) {
            options.put(mode.toString(), mode.toString());
        }
        return options;
    }

    private static String draftModeLabel(String key) {
        if (STANDARD_DRAFT.equals(key)) {
            return "Standard Franken Draft";
        }
        FrankenDraftMode mode = FrankenDraftMode.fromString(key);
        return mode == null ? key : mode.getAutoCompleteName();
    }

    private static Set<Entry<String, FactionModel>> legalFactionOptions(Game game) {
        return FrankenDraft.getAllFrankenLegalFactions(game).stream()
                .collect(Collectors.toMap(FactionModel::getAlias, faction -> faction))
                .entrySet();
    }

    private static Set<Entry<String, FrankenBanList>> banListOptions() {
        return FrankenBanList.getAllBanLists().stream()
                .collect(Collectors.toMap(FrankenBanList::toString, banList -> banList))
                .entrySet();
    }

    @ButtonHandler("frankenSetup")
    public static void setup(ButtonInteractionEvent event, Game game) {
        FrankenSettings menu = game.initializeFrankenSettings();
        menu.postMessageAndButtons(event);
        ButtonHelper.deleteMessage(event);
    }
}
