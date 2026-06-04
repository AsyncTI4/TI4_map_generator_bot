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
import ti4.game.Game;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.ListSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import tools.jackson.databind.JsonNode;

@Getter
@JsonIgnoreProperties("messageId")
public class BaseGameMiniMiltyFactionSettings extends SettingsMenu {

    private static final String MENU_ID = "dsFaction";
    private static final List<ComponentSource> FACTION_SOURCES = List.of(ComponentSource.base);

    private final IntegerSetting numFactions;
    private final ListSetting<FactionModel> banFactions;
    private final ListSetting<FactionModel> priFactions;

    public BaseGameMiniMiltyFactionSettings(Game game, JsonNode json, BaseGameMiniMiltySettings parent) {
        super(MENU_ID, "Faction Settings", "Control faction draft options.", parent);

        int players = parent.getPlayerUserIds().size();
        numFactions = new IntegerSetting("#Factions", "Number of Factions", players + 1, 2, 30, 1);

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

        banFactions.setGetEmoji(FactionModel::getFactionEmoji);
        priFactions.setGetEmoji(FactionModel::getFactionEmoji);

        banFactions.setShow(FactionModel::getAlias);
        priFactions.setShow(FactionModel::getAlias);
        priFactions.setExtraInfo("These factions will be included in the draft first!");

        if (!(json == null
                || !json.has("menuId")
                || !MENU_ID.equals(json.get("menuId").asString("")))) {
            numFactions.initialize(json.get("numFactions"));
            banFactions.initialize(json.get("banFactions"));
            priFactions.initialize(json.get("priFactions"));
        }

        updateTransientSettings();
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
        Map<String, FactionModel> allFactions = getAvailableBaseFactions();
        banFactions.setAllValues(allFactions);
        priFactions.setAllValues(allFactions);
    }

    private static Map<String, FactionModel> getAvailableBaseFactions() {
        return Mapper.getFactionsValues().stream()
                .filter(model -> FACTION_SOURCES.contains(model.getSource()))
                .collect(Collectors.toMap(FactionModel::getAlias, f -> f));
    }
}
