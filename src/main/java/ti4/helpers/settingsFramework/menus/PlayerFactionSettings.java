package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ti4.generator.Mapper;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.helpers.settingsFramework.settings.ListSetting;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;

// This is a sub-menu
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlayerFactionSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    private BooleanSetting presetDraftOrder;
    private ListSetting<Player> gamePlayers;
    private ListSetting<FactionModel> banFactions, priFactions;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    public void finishInitialization(Game game, SettingsMenu parent) {
        this.menuId = "players";
        this.menuName = "Players and Factions";
        this.description = "Adjust which players are actually playing, draft order, and stuff like that";

        // Initialize defaults, including any values loaded from JSON
        presetDraftOrder = new BooleanSetting("StaticOrder", "static draft order", false, presetDraftOrder);

        // Initialize values & keys for gamePlayers
        Set<Entry<String, Player>> allPlayers = game.getPlayers().entrySet();
        Set<String> defaultPlayers = game.getPlayers().values().stream().map(Player::getUserID).collect(Collectors.toSet());
        Set<String> players = Optional.ofNullable(gamePlayers).map(ListSetting::getKeys).orElse(defaultPlayers);
        gamePlayers = new ListSetting<>("Players", "Players playing", "Add player", "Remove player", allPlayers, players, defaultPlayers);

        // Initialize values & keys for ban/priority factions
        List<ComponentSource> sources = new ArrayList<>(Arrays.asList(ComponentSource.pok, ComponentSource.base, ComponentSource.codex3));
        Set<Entry<String, FactionModel>> allFactions = Mapper.getFactions().stream()
            .filter(model -> sources.contains(model.getSource()))
            .collect(Collectors.toMap(f -> f.getAlias(), f -> f)).entrySet();
        Set<String> defaultFactions = new HashSet<>();
        Set<String> bannedFactions = Optional.ofNullable(banFactions).map(ListSetting::getKeys).orElse(defaultFactions);
        Set<String> priorityFactions = Optional.ofNullable(priFactions).map(ListSetting::getKeys).orElse(defaultFactions);
        banFactions = new ListSetting<>("BanFactions", "Banned factions", "Ban faction", "Unban faction", allFactions, bannedFactions, defaultFactions);
        priFactions = new ListSetting<>("PriFactions", "Prioritized factions", "Prioritize faction", "Unprioritize faction", allFactions, priorityFactions, defaultFactions);

        // Set up some other data
        banFactions.setShow(FactionModel::getAlias);
        gamePlayers.setShow(Player::getUserName);
        priFactions.setShow(FactionModel::getAlias);

        banFactions.setGetEmoji(FactionModel::getFactionEmoji);
        priFactions.setGetEmoji(FactionModel::getFactionEmoji);

        presetDraftOrder.setExtraInfo("Use `/game set_order` to change the draft order before starting");

        super.finishInitialization(game, parent);
    }

    @Override
    public List<SettingInterface> settings() {
        updateTransientSettings();
        List<SettingInterface> ls = new ArrayList<SettingInterface>();
        ls.add(gamePlayers);
        ls.add(presetDraftOrder);
        ls.add(banFactions);
        ls.add(priFactions);
        return ls;
    }

    public void updateTransientSettings() {
        if (parent instanceof MiltySettings m) {
            List<ComponentSource> sources = m.getSourceSettings().getFactionSources();
            Map<String, FactionModel> allFactions = Mapper.getFactions().stream()
                .filter(model -> sources.contains(model.getSource()))
                .collect(Collectors.toMap(f -> f.getAlias(), f -> f));
            banFactions.setAllValues(allFactions);
            priFactions.setAllValues(allFactions);

            if (m.getGame().getPlayers().size() != gamePlayers.getAllValues().size())
                gamePlayers.setAllValues(m.getGame().getPlayers());
        }
    }
}
