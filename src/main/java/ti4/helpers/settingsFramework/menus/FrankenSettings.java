package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.function.Consumers;
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
import ti4.logging.BotLogger;
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
    private final FrankenComponentBanSettings componentBanSettings;

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
                new ListSetting<FactionModel>(
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

        componentBanSettings =
                new FrankenComponentBanSettings(game, json != null ? json.get("componentBanSettings") : null, this);

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
        return List.of(componentBanSettings);
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
        componentBanSettings.applyBanSettings(banService);
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
                .filter(banList -> banList != null)
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

@Getter
class FrankenComponentBanSettings extends SettingsMenu {

    private static final String MENU_ID = "frankenComponents";
    private static final String KEY_SEPARATOR = "|";
    private static final int PAGE_SIZE = 25;
    private static final int MAX_SELECT_MENUS_PER_MESSAGE = 5;
    private static final int SUMMARY_LIMIT = 20;
    private static final Map<String, String> COMPONENT_TYPES = componentTypes();

    private Set<String> bannedComponents = new HashSet<>();

    @JsonIgnore
    private final Game game;

    FrankenComponentBanSettings(@NotNull Game game, JsonNode json, SettingsMenu parent) {
        super(MENU_ID, "Component Ban Settings", "Ban individual Franken draft components.", parent);
        this.game = game;

        if (FrankenSettings.isMenuJson(json, MENU_ID)) {
            initializeBannedComponents(json.get("bannedComponents"));
        }
    }

    @Override
    protected List<SettingInterface> settings() {
        return List.of();
    }

    @Override
    protected List<Button> specialButtons() {
        String prefix = menuAction + "_" + navId() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, String> componentType : COMPONENT_TYPES.entrySet()) {
            buttons.add(Buttons.green(
                    prefix + "componentBanPick~add~" + componentType.getKey() + "~0",
                    "Ban " + componentType.getValue()));
        }
        if (!bannedComponents.isEmpty()) {
            for (Map.Entry<String, String> componentType : COMPONENT_TYPES.entrySet()) {
                if (hasBannedComponentOfType(componentType.getKey())) {
                    buttons.add(Buttons.red(
                            prefix + "componentBanPick~remove~" + componentType.getKey() + "~0",
                            "Unban " + componentType.getValue()));
                }
            }
            buttons.add(Buttons.red(prefix + "clearComponentBans", "Clear Component Bans"));
        }
        return buttons;
    }

    @Override
    protected String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        if (action.startsWith("componentBanPick~")) {
            String error = showComponentBanPicker(event, action);
            return error == null ? "success" : error;
        }
        if (action.startsWith("componentBanSelect~")) {
            String error = updateComponentBansFromSelection(event, action);
            return error == null ? "success" : error;
        }
        if ("clearComponentBans".equals(action)) {
            String error = clearComponentBans();
            return error == null ? "success" : error;
        }
        return null;
    }

    @Override
    public String menuSummaryString(String lastSettingTouched) {
        StringBuilder sb = new StringBuilder("# **__").append(menuName).append(":__**");
        for (String line : description) sb.append("\n- *").append(line).append("*");
        sb.append("\n");
        appendComponentSummary(sb);
        return sb.toString();
    }

    @Override
    public String shortSummaryString(boolean shortDescrOnly) {
        StringBuilder sb = new StringBuilder("**__" + menuName + ":__**");
        if (shortDescrOnly) {
            for (String line : description) {
                sb.append("\n- *").append(line).append("*");
                break;
            }
            return sb.toString();
        }
        appendComponentSummary(sb);
        return sb.toString();
    }

    void applyBanSettings(BanService banService) {
        for (String componentKey : bannedComponents) {
            String[] parts = splitComponentKey(componentKey);
            if (parts.length != 2) {
                continue;
            }
            banService.applyOption(game, parts[0], parts[1]);
        }
    }

    private String showComponentBanPicker(GenericInteractionCreateEvent event, String action) {
        if (!(event instanceof ButtonInteractionEvent buttonEvent)) {
            return "Unknown Event";
        }

        ComponentAction componentAction = parseComponentAction(action);
        if (componentAction == null) {
            return "Invalid component ban action.";
        }

        List<Map.Entry<String, String>> entries = componentOptions(game, componentAction.componentType()).stream()
                .filter(entry -> componentAction.add() != bannedComponents.contains(entry.getKey()))
                .sorted(Map.Entry.comparingByValue())
                .toList();
        if (entries.isEmpty()) {
            return "No " + COMPONENT_TYPES.get(componentAction.componentType()) + " components available to "
                    + (componentAction.add() ? "ban." : "unban.");
        }

        List<List<Map.Entry<String, String>>> optionGroups = ListUtils.partition(entries, PAGE_SIZE);
        int messageCount = Math.ceilDiv(optionGroups.size(), MAX_SELECT_MENUS_PER_MESSAGE);
        String prefix = menuAction + "_" + navId() + "_componentBan";

        buttonEvent
                .getHook()
                .sendMessage(componentPickerMessage(componentAction, optionGroups, 0, messageCount))
                .addComponents(componentPickerRows(componentAction, prefix, optionGroups, 0))
                .setEphemeral(true)
                .queue(Consumers.nop(), BotLogger::catchRestError);

        for (int messageIndex = 1; messageIndex < messageCount; messageIndex++) {
            int firstGroup = messageIndex * MAX_SELECT_MENUS_PER_MESSAGE;
            buttonEvent
                    .getHook()
                    .sendMessage(componentPickerMessage(componentAction, optionGroups, messageIndex, messageCount))
                    .addComponents(componentPickerRows(componentAction, prefix, optionGroups, firstGroup))
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
        return null;
    }

    private static String componentPickerMessage(
            ComponentAction componentAction,
            List<List<Map.Entry<String, String>>> optionGroups,
            int messageIndex,
            int messageCount) {
        int firstGroup = messageIndex * MAX_SELECT_MENUS_PER_MESSAGE;
        int lastGroup = Math.min(firstGroup + MAX_SELECT_MENUS_PER_MESSAGE, optionGroups.size());
        StringBuilder message = new StringBuilder(
                componentAction.add() ? "Choose components to ban: " : "Choose components to unban: ");
        message.append(COMPONENT_TYPES.get(componentAction.componentType()));
        if (messageCount > 1) {
            message.append(" (")
                    .append(messageIndex + 1)
                    .append("/")
                    .append(messageCount)
                    .append(")");
        }
        for (int groupIndex = firstGroup; groupIndex < lastGroup; groupIndex++) {
            message.append("\n- List ")
                    .append(groupIndex + 1)
                    .append(": ")
                    .append(alphabetRange(optionGroups.get(groupIndex)));
        }
        return message.toString();
    }

    private static List<ActionRow> componentPickerRows(
            ComponentAction componentAction,
            String prefix,
            List<List<Map.Entry<String, String>>> optionGroups,
            int firstGroup) {
        String mode = componentAction.add() ? "add" : "remove";
        int lastGroup = Math.min(firstGroup + MAX_SELECT_MENUS_PER_MESSAGE, optionGroups.size());
        List<ActionRow> rows = new ArrayList<>();
        for (int groupIndex = firstGroup; groupIndex < lastGroup; groupIndex++) {
            List<Map.Entry<String, String>> group = optionGroups.get(groupIndex);
            List<SelectOption> options = group.stream()
                    .map(entry -> SelectOption.of(entry.getValue(), entry.getKey()))
                    .toList();
            String selectId = prefix + "Select~" + mode + "~" + componentAction.componentType() + "~" + groupIndex;
            StringSelectMenu selectMenu = StringSelectMenu.create(selectId)
                    .addOptions(options)
                    .setPlaceholder((componentAction.add() ? "Ban " : "Unban ")
                            + COMPONENT_TYPES.get(componentAction.componentType()) + " "
                            + alphabetRange(group))
                    .setRequiredRange(0, options.size())
                    .build();
            rows.add(ActionRow.of(selectMenu));
        }
        return rows;
    }

    private String updateComponentBansFromSelection(GenericInteractionCreateEvent event, String action) {
        if (!(event instanceof StringSelectInteractionEvent selectEvent)) {
            return "Unknown Event";
        }

        ComponentAction componentAction = parseComponentAction(action);
        if (componentAction == null) {
            return "Invalid component ban action.";
        }
        for (String key : selectEvent.getValues()) {
            String[] parts = splitComponentKey(key);
            if (parts.length != 2 || !parts[0].equals(componentAction.componentType())) {
                continue;
            }
            if (componentAction.add()) {
                bannedComponents.add(key);
            } else {
                bannedComponents.remove(key);
            }
        }
        return null;
    }

    private String clearComponentBans() {
        bannedComponents.clear();
        return null;
    }

    private void appendComponentSummary(StringBuilder sb) {
        Map<String, Set<String>> banListSources = selectedBanListComponentSources();
        Set<String> effectiveBans = new HashSet<>(bannedComponents);
        effectiveBans.addAll(banListSources.keySet());
        if (effectiveBans.isEmpty()) {
            sb.append("> No component bans selected.\n");
            return;
        }

        List<String> labels = effectiveBans.stream()
                .map(key -> componentLabel(key, banListSources.getOrDefault(key, Set.of())))
                .sorted()
                .toList();
        List<String> shown = labels.subList(0, Math.min(labels.size(), SUMMARY_LIMIT));
        for (String label : shown) {
            sb.append("> ").append(label).append("\n");
        }
        if (labels.size() > SUMMARY_LIMIT) {
            sb.append("> +").append(labels.size() - SUMMARY_LIMIT).append(" more\n");
        }
    }

    private Map<String, Set<String>> selectedBanListComponentSources() {
        Map<String, Set<String>> sources = new LinkedHashMap<>();
        if (!(parent instanceof FrankenSettings frankenSettings)) {
            return sources;
        }
        for (String type : COMPONENT_TYPES.keySet()) {
            for (Map.Entry<String, Set<String>> entry :
                    frankenSettings.selectedBanListSources(type).entrySet()) {
                sources.put(componentKey(type, entry.getKey()), entry.getValue());
            }
        }
        return sources;
    }

    private void initializeBannedComponents(JsonNode json) {
        if (json == null || !json.isArray()) {
            return;
        }
        bannedComponents.clear();
        json.forEach(node -> {
            if (!node.isTextual()) {
                return;
            }
            String key = node.asText();
            String[] parts = splitComponentKey(key);
            if (parts.length == 2 && isValidComponent(game, parts[0], parts[1])) {
                bannedComponents.add(key);
            }
        });
    }

    static boolean isValidComponent(Game game, String type, String id) {
        return switch (type) {
            case Constants.ABILITY ->
                Mapper.getAbility(id) != null && playableFactionHasComponent(game, id, FactionModel::getAbilities);
            case Constants.BREAKTHROUGH ->
                Mapper.getBreakthrough(id) != null
                        && playableFactions(game).stream().anyMatch(faction -> id.equals(faction.getBreakthrough()));
            case Constants.LEADER ->
                Mapper.getLeader(id) != null && playableFactionHasComponent(game, id, FactionModel::getLeaders);
            case Constants.PROMISSORY_NOTE_ID ->
                Mapper.getPromissoryNote(id) != null
                        && playableFactionHasComponent(game, id, FactionModel::getPromissoryNotes);
            case Constants.TECH ->
                Mapper.getTech(id) != null && playableFactionHasComponent(game, id, FactionModel::getFactionTech);
            case Constants.TILE_NAME -> Mapper.getTileRepresentations().containsKey(id);
            case Constants.UNIT_ID ->
                isMechOrFlagship(id) && playableFactionHasComponent(game, id, FactionModel::getUnits);
            case Constants.BAN_COMMODITIES, Constants.BAN_FLEET, Constants.BAN_HS, Constants.BAN_STARTING_TECH ->
                playableFactionAliases(game).contains(id);
            default -> false;
        };
    }

    static Set<Entry<String, String>> componentOptions(Game game, String type) {
        Map<String, String> options = new LinkedHashMap<>();
        List<FactionModel> factions = playableFactions(game);
        switch (type) {
            case Constants.ABILITY ->
                addFactionComponents(
                        options, type, factions, FactionModel::getAbilities, FrankenComponentBanSettings::abilityName);
            case Constants.BREAKTHROUGH ->
                addFactionComponents(
                        options,
                        type,
                        factions,
                        FrankenComponentBanSettings::breakthroughIds,
                        FrankenComponentBanSettings::breakthroughName);
            case Constants.LEADER ->
                addFactionComponents(
                        options, type, factions, FactionModel::getLeaders, FrankenComponentBanSettings::leaderName);
            case Constants.PROMISSORY_NOTE_ID ->
                addFactionComponents(
                        options,
                        type,
                        factions,
                        FactionModel::getPromissoryNotes,
                        FrankenComponentBanSettings::promissoryName);
            case Constants.TECH ->
                addFactionComponents(
                        options, type, factions, FactionModel::getFactionTech, FrankenComponentBanSettings::techName);
            case Constants.TILE_NAME ->
                Mapper.getTileRepresentations().forEach((id, name) -> addOption(options, type, id, name, id));
            case Constants.UNIT_ID ->
                addFactionComponents(
                        options,
                        type,
                        factions,
                        FrankenComponentBanSettings::unitIds,
                        FrankenComponentBanSettings::unitName);
            case Constants.BAN_COMMODITIES, Constants.BAN_FLEET, Constants.BAN_HS, Constants.BAN_STARTING_TECH ->
                addFactionOptions(options, type, factions);
            default -> {}
        }
        return options.entrySet();
    }

    private static void addFactionComponents(
            Map<String, String> options,
            String type,
            List<FactionModel> factions,
            Function<FactionModel, List<String>> componentIds,
            Function<String, String> componentName) {
        for (FactionModel faction : factions) {
            List<String> ids = componentIds.apply(faction);
            if (ids == null) {
                continue;
            }
            for (String id : ids) {
                addOption(options, type, id, componentName.apply(id), id);
            }
        }
    }

    private static void addFactionOptions(Map<String, String> options, String type, List<FactionModel> factions) {
        for (FactionModel faction : factions) {
            addOption(options, type, faction.getAlias(), faction.getFactionName(), faction.getAlias());
        }
    }

    private static void addOption(
            Map<String, String> options, String type, String id, String name, String labelSuffix) {
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            return;
        }
        options.put(componentKey(type, id), abbreviate(name + " [" + labelSuffix + "]"));
    }

    private static List<String> breakthroughIds(FactionModel faction) {
        String id = faction.getBreakthrough();
        return id == null || id.isBlank() ? List.of() : List.of(id);
    }

    private static List<String> unitIds(FactionModel faction) {
        return faction.getUnits().stream()
                .filter(FrankenComponentBanSettings::isMechOrFlagship)
                .toList();
    }

    private static boolean isMechOrFlagship(String id) {
        var unit = Mapper.getUnit(id);
        if (unit == null) {
            return false;
        }
        String baseType = unit.getBaseType();
        return Constants.MECH_ID.equalsIgnoreCase(baseType) || Constants.FLAGSHIP_ID.equalsIgnoreCase(baseType);
    }

    private static String abilityName(String id) {
        var model = Mapper.getAbility(id);
        return model == null ? null : model.getName();
    }

    private static String breakthroughName(String id) {
        var model = Mapper.getBreakthrough(id);
        return model == null ? null : model.getName();
    }

    private static String leaderName(String id) {
        var model = Mapper.getLeader(id);
        return model == null ? null : model.getName();
    }

    private static String promissoryName(String id) {
        var model = Mapper.getPromissoryNote(id);
        return model == null ? null : model.getName();
    }

    private static String techName(String id) {
        var model = Mapper.getTech(id);
        return model == null ? null : model.getName();
    }

    private static String unitName(String id) {
        var model = Mapper.getUnit(id);
        return model == null ? null : model.getName();
    }

    private static List<FactionModel> playableFactions(Game game) {
        return FrankenDraft.getAllFrankenLegalFactions(game);
    }

    private static Set<String> playableFactionAliases(Game game) {
        return playableFactions(game).stream().map(FactionModel::getAlias).collect(Collectors.toSet());
    }

    private static boolean playableFactionHasComponent(
            Game game, String id, Function<FactionModel, List<String>> componentIds) {
        return playableFactions(game).stream().map(componentIds).anyMatch(ids -> ids != null && ids.contains(id));
    }

    private static String componentKey(String type, String id) {
        return type + KEY_SEPARATOR + id;
    }

    private static String[] splitComponentKey(String key) {
        return key.split("\\" + KEY_SEPARATOR, 2);
    }

    private boolean hasBannedComponentOfType(String type) {
        return bannedComponents.stream().anyMatch(key -> key.startsWith(type + KEY_SEPARATOR));
    }

    private static String componentLabel(String key, Set<String> sources) {
        return FrankenSettings.withSourceLabel(componentLabel(key), sources);
    }

    private static String componentLabel(String key) {
        String[] parts = splitComponentKey(key);
        if (parts.length != 2) {
            return key;
        }

        String type = parts[0];
        String id = parts[1];
        String name =
                switch (type) {
                    case Constants.ABILITY -> labelOrId(abilityName(id), id);
                    case Constants.BREAKTHROUGH -> labelOrId(breakthroughName(id), id);
                    case Constants.LEADER -> labelOrId(leaderName(id), id);
                    case Constants.PROMISSORY_NOTE_ID -> labelOrId(promissoryName(id), id);
                    case Constants.TECH -> labelOrId(techName(id), id);
                    case Constants.TILE_NAME -> Mapper.getTileRepresentations().getOrDefault(id, id);
                    case Constants.UNIT_ID -> labelOrId(unitName(id), id);
                    case Constants.BAN_COMMODITIES -> FrankenSettings.factionName(id) + " commodities";
                    case Constants.BAN_FLEET -> FrankenSettings.factionName(id) + " starting fleet";
                    case Constants.BAN_HS -> FrankenSettings.factionName(id) + " home system";
                    case Constants.BAN_STARTING_TECH -> FrankenSettings.factionName(id) + " starting tech";
                    default -> id;
                };

        return "`" + type + "`: " + name + " [" + id + "]";
    }

    private static String labelOrId(String label, String id) {
        return label == null ? id : label;
    }

    private static ComponentAction parseComponentAction(String action) {
        String[] parts = action.split("~");
        if (parts.length < 4) {
            return null;
        }
        boolean add;
        if ("add".equals(parts[1])) {
            add = true;
        } else if ("remove".equals(parts[1])) {
            add = false;
        } else {
            return null;
        }
        String componentType = parts[2];
        if (!COMPONENT_TYPES.containsKey(componentType)) {
            return null;
        }
        int page;
        try {
            page = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            page = 0;
        }
        return new ComponentAction(add, componentType, page);
    }

    private static Map<String, String> componentTypes() {
        Map<String, String> componentTypes = new LinkedHashMap<>();
        componentTypes.put(Constants.ABILITY, "Ability");
        componentTypes.put(Constants.BREAKTHROUGH, "Breakthrough");
        componentTypes.put(Constants.LEADER, "Leader");
        componentTypes.put(Constants.PROMISSORY_NOTE_ID, "Promissory");
        componentTypes.put(Constants.TECH, "Tech");
        componentTypes.put(Constants.TILE_NAME, "Tile");
        componentTypes.put(Constants.UNIT_ID, "Unit");
        componentTypes.put(Constants.BAN_COMMODITIES, "Commodities");
        componentTypes.put(Constants.BAN_FLEET, "Starting Fleet");
        componentTypes.put(Constants.BAN_HS, "Home System");
        componentTypes.put(Constants.BAN_STARTING_TECH, "Starting Tech");
        return componentTypes;
    }

    private static String abbreviate(String label) {
        if (label.length() <= 90) {
            return label;
        }
        return label.substring(0, 87) + "...";
    }

    private static String alphabetRange(List<Map.Entry<String, String>> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        String first = alphabetStart(entries.getFirst().getValue());
        String last = alphabetStart(entries.getLast().getValue());
        return first.equals(last) ? first : first + "-" + last;
    }

    private static String alphabetStart(String label) {
        if (label == null || label.isBlank()) {
            return "#";
        }
        char first = Character.toUpperCase(label.trim().charAt(0));
        return Character.isLetterOrDigit(first) ? String.valueOf(first) : "#";
    }

    private record ComponentAction(boolean add, String componentType, int page) {}
}
