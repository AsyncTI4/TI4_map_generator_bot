package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.ListSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import tools.jackson.databind.JsonNode;

@Getter
@JsonIgnoreProperties("messageId")
public class MahactKingDraftableSettings extends SettingsMenu {

    // Setting
    private final IntegerSetting numFactions;
    private final ListSetting<FactionModel> banFactions;
    private final ListSetting<FactionModel> priFactions;

    private static final String MENU_ID = "dsMahactKing";

    public MahactKingDraftableSettings(Game game, JsonNode json, DraftSystemSettings parent) {
        super(MENU_ID, "Mahact King Settings", "Control mahact king draft options.", parent);

        int players = parent.getPlayerUserIds().size();
        numFactions = new IntegerSetting("#Kings", "Number of Kings", players, 2, 24, 1);
        // Initialize values & keys for ban/priority factions
        Set<String> empty = new HashSet<>();
        Set<Entry<String, FactionModel>> allFactions = new HashSet<>();
        banFactions =
                new ListSetting<>("BanKings", "Banned kings", "Ban king", "Unban king", allFactions, empty, empty);
        priFactions = new ListSetting<>(
                "PriKings", "Prioritized kings", "Prioritize king", "Unprioritize king", allFactions, empty, empty);

        // Emojis
        banFactions.setGetEmoji(FactionModel::getFactionEmoji);
        priFactions.setGetEmoji(FactionModel::getFactionEmoji);

        // Other Initialization
        banFactions.setShow(FactionModel::getAlias);
        priFactions.setShow(FactionModel::getAlias);
        priFactions.setExtraInfo("These kings will be included in the draft first!");

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
    protected void updateTransientSettings() {
        List<ComponentSource> sources = List.of(ComponentSource.twilights_fall);
        Map<String, FactionModel> allFactions = Mapper.getFactionsValues().stream()
                .filter(model -> sources.contains(model.getSource()))
                .collect(Collectors.toMap(FactionModel::getAlias, f -> f));
        banFactions.setAllValues(allFactions);
        priFactions.setAllValues(allFactions);
    }
}
