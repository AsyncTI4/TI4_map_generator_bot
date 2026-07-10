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
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.ListSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.service.franken.FrankenBanList;
import ti4.service.franken.FrankenDraftMode;
import ti4.service.franken.FrankenDraftStartService;
import tools.jackson.databind.JsonNode;

@Getter
public class FrankenSettings extends SettingsMenu {

    private static final String MENU_ID = "franken";
    static final String STANDARD_DRAFT = "standard";
    private static final Set<String> ALWAYS_DISABLED_FACTIONS = Set.of(
            "lazax",
            "admins",
            "franken",
            "keleresm",
            "keleresx",
            "miltymod",
            "qulane",
            "neutral",
            "kaltrim",
            "xin",
            "sarcosa",
            "obsidian");

    private final ChoiceSetting<String> draftMode;
    private final BooleanSetting force;
    private final BooleanSetting banAllDsFactions;
    private final BooleanSetting banAllBrFactions;
    private final ListSetting<FactionModel> bannedFactions;
    private final ListSetting<FrankenBanList> banLists;
    private final FrankenHomebrewSettings homebrewSettings;

    @JsonIgnore
    private final FrankenDraftLimitSettings draftLimitSettings;

    @JsonIgnore
    private final Game game;

    public FrankenSettings(@NotNull Game game, JsonNode json) {
        super(MENU_ID, "Franken Settings", "Edit franken draft settings, then start the draft!", null);
        this.game = game;

        draftMode = new ChoiceSetting<>("DraftMode", "Draft Mode", STANDARD_DRAFT);
        draftMode.setAllValues(draftModeOptions());
        draftMode.setShow(FrankenSettings::draftModeLabel);

        force = new BooleanSetting("Force", "Force overwrite existing player setups", false);
        banAllDsFactions = new BooleanSetting("BanAllDS", "DS Factions", true);
        banAllBrFactions = new BooleanSetting("BanAllBR", "BR Factions", true);

        Set<String> empty = new HashSet<>();
        bannedFactions =
                new ListSetting<>(
                        "BannedFactions",
                        "Banned Factions",
                        "Ban faction",
                        "Unban faction",
                        legalFactionOptions(game.isThundersEdge(), false, false, false)
                                .entrySet(),
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
            banAllDsFactions.initialize(json.get("banAllDsFactions"));
            banAllBrFactions.initialize(json.get("banAllBrFactions"));
            bannedFactions.initialize(json.get("bannedFactions"));
            banLists.initialize(json.get("banLists"));
        }

        homebrewSettings = new FrankenHomebrewSettings(game, json, this);
        draftLimitSettings = new FrankenDraftLimitSettings(game, json, this);

        if (json != null && json.has("messageId")) {
            setMessageId(json.get("messageId").asText(null));
        }
    }

    @Override
    protected List<SettingInterface> settings() {
        List<SettingInterface> settings = new ArrayList<>(List.of(draftMode, force));
        if (isFrankendrazMode()) {
            settings.add(banAllDsFactions);
            settings.add(banAllBrFactions);
        }
        settings.add(bannedFactions);
        if (!isFrankendrazMode()) {
            settings.add(banLists);
        }
        return settings;
    }

    @Override
    protected List<SettingsMenu> categories() {
        return List.of(homebrewSettings, draftLimitSettings);
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
        if (lastSettingTouched == null
                || "DraftMode".equals(lastSettingTouched)
                || "BanAllDS".equals(lastSettingTouched)
                || "BanAllBR".equals(lastSettingTouched)
                || "DiscoStars".equals(lastSettingTouched)
                || "BlueReverie".equals(lastSettingTouched)) {
            syncFrankendrazDsBrState(lastSettingTouched);
        }
        String summary = super.menuSummaryString(lastSettingTouched) + frankenNotes();
        game.setFrankenSettings(this);
        game.setFrankenSettingsJson(json());
        return summary;
    }

    @Override
    protected String resetSettings() {
        String err = super.resetSettings();
        if (err != null) return err;
        homebrewSettings.resetSettings();
        draftLimitSettings.resetSettings();
        if (isFrankendrazMode()) {
            banAllDsFactions.setVal(true);
            banAllBrFactions.setVal(true);
            homebrewSettings.getDiscoStars().setVal(true);
            homebrewSettings.getBlueReverie().setVal(true);
        }
        return null;
    }

    private String startDraft(GenericInteractionCreateEvent event) {
        applyHomebrewSettings();
        String validationError = FrankenDraftStartService.validateStartFrankenDraft(game, force.isVal());
        if (validationError != null) {
            return validationError;
        }
        applyBanSettings();
        return FrankenDraftStartService.startFrankenDraft(event, game, force.isVal(), selectedDraftMode());
    }

    private void applyBanSettings() {
        game.setStoredValue("bannedFactions", "");
        BanService banService = new BanService();
        applySourceFactionBans(banService, ComponentSource.ds, !banAllDsFactions.isVal());
        applySourceFactionBans(banService, ComponentSource.blue_reverie, !banAllBrFactions.isVal());
        for (String faction : bannedFactions.getKeys()) {
            banService.applyOption(game, Constants.BAN_FACTION, faction);
        }
        for (String banList : banLists.getKeys()) {
            banService.applyBanList(game, FrankenBanList.fromString(banList));
        }
    }

    private void applySourceFactionBans(BanService banService, ComponentSource source, boolean enabled) {
        if (!enabled) return;
        Mapper.getFactionsValues().stream()
                .filter(faction -> faction.getSource() == source)
                .map(FactionModel::getAlias)
                .forEach(faction -> banService.applyOption(game, Constants.BAN_FACTION, faction));
    }

    @Override
    protected void updateTransientSettings() {
        bannedFactions.setAllValues(legalFactionOptions(
                game.isThundersEdge(),
                isEffectiveDiscordantStarsEnabled(),
                isEffectiveBlueReverieEnabled(),
                isLostLegaciesEnabled()));
    }

    void applyHomebrewSettings() {
        game.setDiscordantStarsMode(isEffectiveDiscordantStarsEnabled());
        game.setBlueReverieMode(isEffectiveBlueReverieEnabled());
        game.setUnchartedSpaceStuff(homebrewSettings.getUnchartedSpace().isVal());
        game.setStoredValue(
                Constants.INCLUDE_ERONOUS_TILES,
                Boolean.toString(homebrewSettings.getEronous().isVal()));
    }

    private FrankenDraftMode selectedDraftMode() {
        String selected = draftMode.getValue();
        if (STANDARD_DRAFT.equals(selected)) {
            return null;
        }
        return FrankenDraftMode.fromString(selected);
    }

    boolean isFrankendrazMode() {
        return FrankenDraftMode.FRANKENDRAZ.toString().equals(draftMode.getValue());
    }

    boolean isEffectiveDiscordantStarsEnabled() {
        return isFrankendrazMode()
                ? banAllDsFactions.isVal()
                : homebrewSettings.getDiscoStars().isVal();
    }

    boolean isEffectiveBlueReverieEnabled() {
        return isFrankendrazMode()
                ? banAllBrFactions.isVal()
                : homebrewSettings.getBlueReverie().isVal();
    }

    public boolean isLostLegaciesEnabled() {
        return homebrewSettings.getLostLegacies().isVal();
    }

    void syncFrankendrazDsBrState(String lastSettingTouched) {
        if (!isFrankendrazMode()) return;
        if (lastSettingTouched == null) {
            banAllDsFactions.setVal(homebrewSettings.getDiscoStars().isVal());
            banAllBrFactions.setVal(homebrewSettings.getBlueReverie().isVal());
            return;
        }
        switch (lastSettingTouched) {
            case "DraftMode" -> {
                banAllDsFactions.setVal(true);
                banAllBrFactions.setVal(true);
                homebrewSettings.getDiscoStars().setVal(true);
                homebrewSettings.getBlueReverie().setVal(true);
            }
            case "BanAllDS" -> homebrewSettings.getDiscoStars().setVal(banAllDsFactions.isVal());
            case "BanAllBR" -> homebrewSettings.getBlueReverie().setVal(banAllBrFactions.isVal());
            case "DiscoStars" ->
                banAllDsFactions.setVal(homebrewSettings.getDiscoStars().isVal());
            case "BlueReverie" ->
                banAllBrFactions.setVal(homebrewSettings.getBlueReverie().isVal());
            default -> {
                banAllDsFactions.setVal(homebrewSettings.getDiscoStars().isVal());
                banAllBrFactions.setVal(homebrewSettings.getBlueReverie().isVal());
            }
        }
    }

    private String effectiveBannedFactionSummary() {
        Map<String, Set<String>> banListSources = selectedBanListSources(Constants.BAN_FACTION);
        Set<String> effectiveBans = new HashSet<>(bannedFactions.getKeys());
        effectiveBans.addAll(banListSources.keySet());
        if (!banAllDsFactions.isVal()) {
            Mapper.getFactionsValues().stream()
                    .filter(f -> f.getSource() == ComponentSource.ds)
                    .filter(f -> !ALWAYS_DISABLED_FACTIONS.contains(f.getAlias()))
                    .map(FactionModel::getAlias)
                    .forEach(effectiveBans::add);
        }

        if (!banAllBrFactions.isVal()) {
            Mapper.getFactionsValues().stream()
                    .filter(f -> f.getSource() == ComponentSource.blue_reverie)
                    .filter(f -> !ALWAYS_DISABLED_FACTIONS.contains(f.getAlias()))
                    .map(FactionModel::getAlias)
                    .forEach(effectiveBans::add);
        }
        List<String> values = effectiveBans.stream()
                .map(key -> factionBanLabel(key, banListSources.getOrDefault(key, Set.of())))
                .sorted()
                .toList();
        return "[" + String.join(",", values) + "]";
    }

    private static String frankenNotes() {
        return """


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

    private static Map<String, FactionModel> legalFactionOptions(
            boolean teEnabled, boolean dsEnabled, boolean brEnabled, boolean lostLegaciesEnabled) {
        return Mapper.getFactionsValues().stream()
                .filter(faction -> isLegalFrankenFaction(faction, teEnabled, dsEnabled, brEnabled, lostLegaciesEnabled))
                .collect(Collectors.toMap(FactionModel::getAlias, faction -> faction));
    }

    private static boolean isLegalFrankenFaction(
            FactionModel faction,
            boolean teEnabled,
            boolean dsEnabled,
            boolean brEnabled,
            boolean lostLegaciesEnabled) {
        String alias = faction.getAlias();
        if (ALWAYS_DISABLED_FACTIONS.contains(alias)) {
            return false;
        }
        if (teEnabled && faction.getSource().isTe()) return true;
        if (dsEnabled && faction.getSource() == ComponentSource.ds) return true;
        if (brEnabled && faction.getSource() == ComponentSource.blue_reverie) return true;
        if (lostLegaciesEnabled && faction.getSource() == ComponentSource.theodisi) return true;
        return faction.getSource().isPok();
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
        // ButtonHelper.deleteMessage(event);
    }
}
