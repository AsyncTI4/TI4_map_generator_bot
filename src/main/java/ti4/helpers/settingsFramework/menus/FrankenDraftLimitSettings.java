package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.draft.DraftCategory;
import ti4.draft.FrankenDraft;
import ti4.draft.FrankenDrazDraft;
import ti4.draft.InauguralSpliceFrankenDraft;
import ti4.draft.OnePickFrankenDraft;
import ti4.draft.OverdraftFrankenDraft;
import ti4.draft.PoweredFrankenDraft;
import ti4.draft.PoweredOnePickFrankenDraft;
import ti4.draft.PoweredOverdraftFrankenDraft;
import ti4.draft.TwilightsFallFrankenDraft;
import ti4.game.Game;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.service.franken.FrankenDraftMode;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties("messageId")
class FrankenDraftLimitSettings extends SettingsMenu {
    private static final String MENU_ID = "draftLimits";
    private static final int MAX_LIMIT = 20;
    private static final List<DraftCategory> CATEGORIES = Arrays.asList(DraftCategory.values());

    @JsonIgnore
    private final Game game;

    @JsonIgnore
    private final FrankenSettings parentSettings;

    @JsonIgnore
    private final List<FrankenLimitSetting> limitSettings = new ArrayList<>();

    @JsonIgnore
    private final Map<DraftCategory, FrankenLimitSetting> limitSettingMap = new LinkedHashMap<>();

    FrankenDraftLimitSettings(Game game, JsonNode json, FrankenSettings parent) {
        super(MENU_ID, "Draft Limits", "Adjust per-category draft limits for the selected Franken mode.", parent);
        this.game = game;
        parentSettings = parent;

        for (DraftCategory category : CATEGORIES) {
            FrankenLimitSetting setting = new FrankenLimitSetting(category, defaultLimit(category));
            limitSettings.add(setting);
            limitSettingMap.put(category, setting);
        }

        updateTransientSettings();

        if (json != null && json.has(MENU_ID)) json = json.get(MENU_ID);
        if (json != null
                && json.has("menuId")
                && MENU_ID.equals(json.get("menuId").asString(""))
                && json.has("limitSettings")
                && json.get("limitSettings").isArray()) {
            json.get("limitSettings").forEach(node -> {
                if (!node.has("id")) return;
                FrankenLimitSetting setting = findSetting(node.get("id").asText());
                if (setting != null) setting.initialize(node);
            });
        }
    }

    @Override
    protected List<SettingInterface> settings() {
        return new ArrayList<>(limitSettings);
    }

    @Override
    protected List<Button> specialButtons() {
        String prefix = menuAction + "_" + navId() + "_";
        return new ArrayList<>(List.of(Buttons.red(prefix + "setAllZero", "Set All Limits To 0")));
    }

    @Override
    protected String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        if (!"setAllZero".equals(action)) return null;
        for (FrankenLimitSetting setting : limitSettings) {
            if (setting.isDisabled()) continue;
            setting.setVal(0);
            setting.persist();
        }
        return "success";
    }

    @Override
    protected void updateTransientSettings() {
        for (DraftCategory category : CATEGORIES) {
            FrankenLimitSetting setting = limitSettingMap.get(category);
            if (setting == null) continue;
            int defaultLimit = defaultLimit(category);
            setting.syncFromGame(defaultLimit);
            setting.setDisabled(!shouldShowCategory(category, defaultLimit));
        }
    }

    @Override
    protected String resetSettings() {
        String err = super.resetSettings();
        if (err != null) return err;
        for (FrankenLimitSetting setting : limitSettings) {
            setting.persist();
        }
        return null;
    }

    private FrankenLimitSetting findSetting(String id) {
        return limitSettings.stream()
                .filter(setting -> setting.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private int defaultLimit(DraftCategory category) {
        String selectedMode = parentSettings.getDraftMode().getValue();
        if (FrankenSettings.STANDARD_DRAFT.equals(selectedMode)) {
            return new FrankenDraft(game).getItemLimitForCategory(category);
        }
        FrankenDraftMode mode = FrankenDraftMode.fromString(selectedMode);
        if (mode == null) {
            return new FrankenDraft(game).getItemLimitForCategory(category);
        }
        return switch (mode) {
            case POWERED -> new PoweredFrankenDraft(game).getItemLimitForCategory(category);
            case ONEPICK -> new OnePickFrankenDraft(game).getItemLimitForCategory(category);
            case OVERDRAFT -> new OverdraftFrankenDraft(game).getItemLimitForCategory(category);
            case POWEREDONEPICK -> new PoweredOnePickFrankenDraft(game).getItemLimitForCategory(category);
            case POWEREDOVERDRAFT -> new PoweredOverdraftFrankenDraft(game).getItemLimitForCategory(category);
            case FRANKENDRAZ -> new FrankenDrazDraft(game).getItemLimitForCategory(category);
            case TWILIGHTSFALL -> new TwilightsFallFrankenDraft(game).getItemLimitForCategory(category);
            case INAUGURALSPLICE -> new InauguralSpliceFrankenDraft(game).getItemLimitForCategory(category);
        };
    }

    private static String categoryName(DraftCategory category) {
        return switch (category) {
            case FACTION -> "Factions";
            case ABILITY -> "Abilities";
            case TECH -> "Faction Techs";
            case BREAKTHROUGH -> "Breakthroughs";
            case AGENT -> "Agents";
            case COMMANDER -> "Commanders";
            case HERO -> "Heroes";
            case MECH -> "Mechs";
            case FLAGSHIP -> "Flagships";
            case COMMODITIES -> "Commodities";
            case PN -> "Promissory Notes";
            case HOMESYSTEM -> "Home Systems";
            case STARTINGTECH -> "Starting Techs";
            case STARTINGFLEET -> "Starting Fleets";
            case BLUETILE -> "Blue-backed Tiles";
            case REDTILE -> "Red-backed Tiles";
            case DRAFTORDER -> "Draft Order";
            case MAHACTKING -> "Mahact Kings";
            case UNIT -> "Units";
            case PLOT -> "Plot Cards";
        };
    }

    private static boolean shouldShowCategory(DraftCategory category, int defaultLimit) {
        return defaultLimit > 0 && category != DraftCategory.DRAFTORDER;
    }

    private final class FrankenLimitSetting extends IntegerSetting {
        private final DraftCategory category;

        private FrankenLimitSetting(DraftCategory category, int defaultValue) {
            super(category.name(), categoryName(category), defaultValue, 0, MAX_LIMIT, 1);
            this.category = category;
        }

        @Override
        public String modify(GenericInteractionCreateEvent event, String action) {
            String err = super.modify(event, action);
            if (err == null) persist();
            return err;
        }

        @Override
        public void reset() {
            super.reset();
            persist();
        }

        private void syncFromGame(int defaultValue) {
            setDefaultValue(defaultValue);
            String stored = game.getStoredValue("frankenLimit" + category);
            if (stored == null || stored.isEmpty()) {
                setVal(defaultValue);
            } else {
                setVal(Integer.parseInt(stored));
            }
        }

        private void persist() {
            if (getVal() == getDefaultValue()) {
                game.removeStoredValue("frankenLimit" + category);
            } else {
                game.setStoredValue("frankenLimit" + category, Integer.toString(getVal()));
            }
        }
    }
}
