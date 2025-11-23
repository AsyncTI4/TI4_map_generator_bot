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
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.ListSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;

@Getter
@JsonIgnoreProperties("messageId")
public class AndcatReferenceCardsDraftableSettings extends SettingsMenu {

    // Setting
    private final IntegerSetting numPackages;
    private final ListSetting<FactionModel> banFactions;

    private static final String MENU_ID = "dsAndcatRefPackage";

    public AndcatReferenceCardsDraftableSettings(Game game, JsonNode json, DraftSystemSettings parent) {
        super(MENU_ID, "Reference Card Package Settings", "Control reference card package options.", parent);

        int players = parent.getPlayerUserIds().size();
        numPackages = new IntegerSetting("#Packages", "Number of Packages", players, 2, 9, 1);
        // Initialize values & keys for ban/priority factions
        Set<String> empty = new HashSet<>();
        Set<Entry<String, FactionModel>> allFactions = new HashSet<>();
        banFactions = new ListSetting<>(
                "BanFactionCards", "Banned faction cards", "Ban faction", "Unban faction", allFactions, empty, empty);

        // Emojis
        banFactions.setGetEmoji(FactionModel::getFactionEmoji);

        // Other Initialization
        banFactions.setShow(FactionModel::getAlias);

        // Finish initializing transient settings here
        updateTransientSettings();

        // Load JSON if applicable
        if (!(json == null
                || !json.has("menuId")
                || !MENU_ID.equals(json.get("menuId").asText("")))) {
            numPackages.initialize(json.get("numPackages"));
            banFactions.initialize(json.get("banFactions"));
        }
    }

    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        ls.add(numPackages);
        ls.add(banFactions);
        return ls;
    }

    @Override
    public List<Button> specialButtons() {
        // String idPrefix = menuAction + "_" + navId() + "_";
        return new ArrayList<>(super.specialButtons());
    }

    @Override
    protected void updateTransientSettings() {
        if (parent instanceof DraftSystemSettings dss) {
            List<ComponentSource> sources = dss.getSourceSettings().getFactionSources();
            Map<String, FactionModel> allFactions = Mapper.getFactionsValues().stream()
                    .filter(model -> sources.contains(model.getSource()))
                    .filter(model -> model.getPriorityNumber() != null)
                    .filter(model -> !model.getAlias().contains("keleres")
                            || "keleresm".equals(model.getAlias())) // Limit the pool to only 1 keleres flavor
                    .collect(Collectors.toMap(FactionModel::getAlias, f -> f));
            banFactions.setAllValues(allFactions);
        }
    }
}
