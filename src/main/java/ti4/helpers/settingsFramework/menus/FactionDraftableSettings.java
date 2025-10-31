package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.ListSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.SourceEmojis;

@Getter
@JsonIgnoreProperties("messageId")
public class FactionDraftableSettings extends SettingsMenu {

    // Setting
    private final IntegerSetting numFactions;
    private final ListSetting<FactionModel> banFactions;
    private final ListSetting<FactionModel> priFactions;

    private static final String MENU_ID = "dsFaction";

    public FactionDraftableSettings(Game game, JsonNode json, DraftSystemSettings parent) {
        super(MENU_ID, "Faction Settings", "Control faction draft options.", parent);

        int players = parent.getPlayerUserIds().size();
        numFactions = new IntegerSetting("#Factions", "Number of Factions", players + 1, 2, 30, 1);
        // Initialize values & keys for ban/priority factions
        Set<String> empty = new HashSet<>();
        Set<Entry<String, FactionModel>> allFactions = new HashSet<>();
        banFactions = new ListSetting<>(
                "BanFactions", "Banned factions", "Ban faction", "Unban faction", allFactions, empty, empty);
        priFactions = new ListSetting<>(
                "PriFactions",
                "Prioritized factions",
                "Prioritize faction",
                "Unprioritize faction",
                allFactions,
                empty,
                empty);

        // Emojis
        banFactions.setGetEmoji(FactionModel::getFactionEmoji);
        priFactions.setGetEmoji(FactionModel::getFactionEmoji);

        // Other Initialization
        banFactions.setShow(FactionModel::getAlias);
        priFactions.setShow(FactionModel::getAlias);
        priFactions.setExtraInfo("These factions will be included in the draft first!");

        // Finish initializing transient settings here
        updateTransientSettings();

        // Load JSON if applicable
        if (!(json == null
                || !json.has("menuId")
                || !MENU_ID.equals(json.get("menuId").asText("")))) {
            numFactions.initialize(json.get("numFactions"));
            banFactions.initialize(json.get("banFactions"));
            priFactions.initialize(json.get("priFactions"));
        }
    }

    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        ls.add(numFactions);
        ls.add(banFactions);
        ls.add(priFactions);
        return ls;
    }

    @Override
    public List<Button> specialButtons() {
        String idPrefix = menuAction + "_" + navId() + "_";
        List<Button> ls = new ArrayList<>(super.specialButtons());

        if (parent != null && parent instanceof MiltySettings ms) {
            if (ms.getSourceSettings().getDiscoStars().isVal())
                ls.add(Buttons.red(
                        idPrefix + "homebrewFactionsOnly", "Only Homebrew Factions", SourceEmojis.DiscordantStars));
        }
        return ls;
    }

    @Override
    public String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error =
                switch (action) {
                    case "homebrewFactionsOnly" -> prioritizeHomebrewFactions();
                    default -> null;
                };

        return (error == null ? "success" : error);
    }

    @Override
    protected void updateTransientSettings() {
        if (parent instanceof DraftSystemSettings dss) {
            List<ComponentSource> sources = dss.getSourceSettings().getFactionSources();
            Map<String, FactionModel> allFactions = Mapper.getFactionsValues().stream()
                    .filter(model -> sources.contains(model.getSource()))
                    .filter(model -> !model.getAlias().contains("keleres")
                            || "keleresm".equals(model.getAlias())) // Limit the pool to only 1 keleres flavor
                    .collect(Collectors.toMap(FactionModel::getAlias, f -> f));
            banFactions.setAllValues(allFactions);
            priFactions.setAllValues(allFactions);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Specific Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------

    private String prioritizeHomebrewFactions() {
        if (parent != null && parent instanceof DraftSystemSettings dss) {
            Set<ComponentSource> sources = new HashSet<>(dss.getSourceSettings().getFactionSources());
            sources.remove(ComponentSource.base);
            sources.remove(ComponentSource.pok);
            sources.remove(ComponentSource.thunders_edge);
            sources.remove(ComponentSource.codex1);
            sources.remove(ComponentSource.codex2);
            sources.remove(ComponentSource.codex3);
            sources.remove(ComponentSource.codex4);

            if (sources.isEmpty()) return "No homebrew faction sources are enabled";

            List<String> newKeys = new ArrayList<>();
            for (FactionModel model : priFactions.getAllValues().values()) {
                if (sources.contains(model.getSource())) newKeys.add(model.getAlias());
            }
            priFactions.setKeys(newKeys);
        }
        return null;
    }
}
