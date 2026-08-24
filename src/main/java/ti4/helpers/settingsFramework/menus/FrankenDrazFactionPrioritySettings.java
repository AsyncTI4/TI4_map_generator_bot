package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.helpers.settingsFramework.settings.IntegerRangeSetting;
import ti4.helpers.settingsFramework.settings.ListSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import tools.jackson.databind.JsonNode;

@Getter
@JsonIgnoreProperties("messageId")
class FrankenDrazFactionPrioritySettings extends SettingsMenu {
    private static final String MENU_ID = "drazFactionPriorities";

    private final ListSetting<FactionModel> prioritizedFactions;
    private final IntegerRangeSetting discordantStarsFactionLimits;
    private final IntegerRangeSetting blueReverieFactionLimits;
    private final IntegerRangeSetting lostLegaciesFactionLimits;

    FrankenDrazFactionPrioritySettings(JsonNode json, SettingsMenu parent) {
        super(
                MENU_ID,
                "FrankenDraz Faction Priorities",
                "Prioritize factions and set source minimums and maximums for the FrankenDraz faction pool.",
                parent);

        Set<String> empty = new HashSet<>();
        prioritizedFactions = new ListSetting<>(
                "PriorityFactions",
                "Prioritized Factions",
                "Prioritize Faction",
                "Unprioritize Faction",
                Map.<String, FactionModel>of().entrySet(),
                empty,
                empty);
        prioritizedFactions.setGetEmoji(FactionModel::getFactionEmoji);
        prioritizedFactions.setShow(FactionModel::getFactionName);
        prioritizedFactions.setExtraInfo("These factions will be included in the draft first.");

        discordantStarsFactionLimits = sourceRange("DiscordantStars", "Discordant Stars Factions", ComponentSource.ds);
        blueReverieFactionLimits = sourceRange("BlueReverie", "Blue Reverie Factions", ComponentSource.blue_reverie);
        lostLegaciesFactionLimits = sourceRange("LostLegacies", "Lost Legacies Factions", ComponentSource.theodisi);

        updateTransientSettings();
        if (json != null && json.has("factionPrioritySettings")) json = json.get("factionPrioritySettings");
        if (FrankenSettings.isMenuJson(json, MENU_ID)) {
            prioritizedFactions.initialize(json.get("prioritizedFactions"));
            if (json.has("discordantStarsFactionLimits")) {
                discordantStarsFactionLimits.initialize(json.get("discordantStarsFactionLimits"));
            }
            if (json.has("blueReverieFactionLimits")) {
                blueReverieFactionLimits.initialize(json.get("blueReverieFactionLimits"));
            }
            if (json.has("lostLegaciesFactionLimits")) {
                lostLegaciesFactionLimits.initialize(json.get("lostLegaciesFactionLimits"));
            }
        }
    }

    @Override
    protected List<SettingInterface> settings() {
        if (!(parent instanceof FrankenSettings settings) || !settings.isFrankendrazMode()) {
            return List.of();
        }
        List<SettingInterface> output = new ArrayList<>(List.of(prioritizedFactions));
        if (settings.isEffectiveDiscordantStarsEnabled()) output.add(discordantStarsFactionLimits);
        if (settings.isEffectiveBlueReverieEnabled()) output.add(blueReverieFactionLimits);
        if (settings.isLostLegaciesEnabled()) output.add(lostLegaciesFactionLimits);
        return output;
    }

    @Override
    protected List<Button> specialButtons() {
        if (!(parent instanceof FrankenSettings settings) || !settings.isFrankendrazMode()) {
            return List.of();
        }
        String prefix = menuAction + "_" + navId() + "_";
        List<Button> buttons = new ArrayList<>();
        if (settings.isEffectiveDiscordantStarsEnabled()) {
            buttons.add(Buttons.blue(prefix + "prioritizeDiscordantStars", "Prioritize Discordant Stars"));
        }
        if (settings.isEffectiveBlueReverieEnabled()) {
            buttons.add(Buttons.blue(prefix + "prioritizeBlueReverie", "Prioritize Blue Reverie"));
        }
        if (settings.isLostLegaciesEnabled()) {
            buttons.add(Buttons.blue(prefix + "prioritizeLostLegacies", "Prioritize Lost Legacies"));
        }
        return buttons;
    }

    @Override
    protected String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        ComponentSource source =
                switch (action) {
                    case "prioritizeDiscordantStars" -> ComponentSource.ds;
                    case "prioritizeBlueReverie" -> ComponentSource.blue_reverie;
                    case "prioritizeLostLegacies" -> ComponentSource.theodisi;
                    default -> null;
                };
        if (source == null) return null;
        List<String> priorities = new ArrayList<>(prioritizedFactions.getKeys());
        prioritizedFactions.getAllValues().values().stream()
                .filter(faction -> faction.getSource() == source)
                .map(FactionModel::getAlias)
                .filter(faction -> !priorities.contains(faction))
                .forEach(priorities::add);
        prioritizedFactions.setKeys(priorities);
        return "success";
    }

    @Override
    public String menuSummaryString(String lastSettingTouched) {
        String summary = super.menuSummaryString(lastSettingTouched);
        if (parent instanceof FrankenSettings settings) {
            settings.persistSettings();
        }
        return summary;
    }

    @Override
    protected void updateTransientSettings() {
        if (parent instanceof FrankenSettings settings) {
            prioritizedFactions.setAllValues(settings.getLegalFactionOptions());
        }
    }

    private static IntegerRangeSetting sourceRange(String id, String name, ComponentSource source) {
        int factionCount = (int) Mapper.getFactionsValues().stream()
                .filter(faction -> faction.getSource() == source)
                .count();
        return new IntegerRangeSetting(id, name, 0, 0, factionCount, factionCount, 0, factionCount, 1);
    }
}
