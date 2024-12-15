package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import ti4.image.Mapper;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.ListSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;

// This is a sub-menu
@Getter
@JsonIgnoreProperties({ "messageId" })
public class PlayerFactionSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    private final BooleanSetting presetDraftOrder;
    private ListSetting<Player> gamePlayers;
    private final ListSetting<FactionModel> banFactions;
    private final ListSetting<FactionModel> priFactions;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------
    public PlayerFactionSettings(Game game, JsonNode json, SettingsMenu parent) {
        super("players", "Players and Factions", "Adjust which players are actually playing, draft order, and stuff like that", parent);

        // Initialize Settings to default values
        presetDraftOrder = new BooleanSetting("StaticOrder", "static draft order", false);

        // Initialize values & keys for gamePlayers
        Set<Entry<String, Player>> allPlayers = game.getPlayers().entrySet();
        Set<String> defaultPlayers = game.getPlayers().values().stream().map(Player::getUserID).collect(Collectors.toSet());
        Set<String> players = Optional.ofNullable(gamePlayers).map(ListSetting::getKeys).orElse(defaultPlayers);
        gamePlayers = new ListSetting<>("Players", "Players playing", "Add player", "Remove player", allPlayers, players, defaultPlayers);

        // Initialize values & keys for ban/priority factions
        Set<String> empty = new HashSet<>();
        Set<Entry<String, FactionModel>> allFactions = new HashSet<>();
        banFactions = new ListSetting<>("BanFactions", "Banned factions", "Ban faction", "Unban faction", allFactions, empty, empty);
        priFactions = new ListSetting<>("PriFactions", "Prioritized factions", "Prioritize faction", "Unprioritize faction", allFactions, empty, empty);

        // Emojis
        banFactions.setGetEmoji(FactionModel::getFactionEmoji);
        priFactions.setGetEmoji(FactionModel::getFactionEmoji);

        // Other Initialization
        banFactions.setShow(FactionModel::getAlias);
        gamePlayers.setShow(Player::getUserName);
        priFactions.setShow(FactionModel::getAlias);
        priFactions.setExtraInfo("These factions will be included in the draft first!");
        presetDraftOrder.setExtraInfo("Use `/game set_order` to change the draft order before starting");

        // Finish initializing transient settings here
        updateTransientSettings();

        // Get the correct JSON node for initialization if applicable.
        // Add additional names here to support new generated JSON as needed.
        if (json != null && json.has("playerSettings")) json = json.get("playerSettings");

        // Verify this is the correct JSON node and continue initialization
        List<String> historicIDs = new ArrayList<>(List.of("players"));
        if (json != null && json.has("menuId") && historicIDs.contains(json.get("menuId").asText(""))) {
            presetDraftOrder.initialize(json.get("presetDraftOrder"));
            gamePlayers.initialize(json.get("gamePlayers"));
            banFactions.initialize(json.get("banFactions"));
            priFactions.initialize(json.get("priFactions"));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        ls.add(gamePlayers);
        ls.add(presetDraftOrder);
        ls.add(banFactions);
        ls.add(priFactions);
        return ls;
    }

    @Override
    protected void updateTransientSettings() {
        if (parent instanceof MiltySettings m) {
            List<ComponentSource> sources = m.getSourceSettings().getFactionSources();
            Map<String, FactionModel> allFactions = Mapper.getFactions().stream()
                .filter(model -> sources.contains(model.getSource()))
                .filter(model -> !model.getAlias().contains("keleres") || model.getAlias().equals("keleresm")) // Limit the pool to only 1 keleres flavor
                .collect(Collectors.toMap(FactionModel::getAlias, f -> f));
            banFactions.setAllValues(allFactions);
            priFactions.setAllValues(allFactions);

            if (m.getGame().getPlayers().size() != gamePlayers.getAllValues().size())
                gamePlayers.setAllValues(m.getGame().getPlayers());
        }
    }
}
