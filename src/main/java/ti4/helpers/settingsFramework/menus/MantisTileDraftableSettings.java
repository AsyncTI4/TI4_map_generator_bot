package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.MapTemplateModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.emoji.TileEmojis;

@Getter
@JsonIgnoreProperties("messageId")
public class MantisTileDraftableSettings extends SettingsMenu {

    // Setting
    private final IntegerSetting mulligans;
    private final IntegerSetting extraBlues;
    private final IntegerSetting extraReds;
    private final ChoiceSetting<MapTemplateModel> mapTemplate;

    private static final String MENU_ID = "mantisTile";

    public MantisTileDraftableSettings(Game game, JsonNode json, SettingsMenu parent) {
        super(MENU_ID, "Mantis Tile settings", "Tile drafting and map building.", parent);

        // Initialize settings
        mulligans = new IntegerSetting("Mulligans", "Max Mulligans", 1, 0, 3, 1);
        extraBlues = new IntegerSetting("ExtraBlues", "Extra Blue Tiles", 0, 0, 2, 1);
        extraReds = new IntegerSetting("ExtraReds", "Extra Red Tiles", 0, 0, 2, 1);
        mapTemplate = new ChoiceSetting<>("Template", "Map Template", "6pStandard");

        int players = game != null ? game.getPlayers().size() : 6;
        Map<String, MapTemplateModel> templates = Mapper.getMapTemplatesForPlayerCount(players).stream()
                .filter(t -> !t.isNucleusTemplate())
                .collect(Collectors.toMap(MapTemplateModel::getAlias, x -> x));
        MapTemplateModel defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(players);
        mapTemplate.setAllValues(templates, defaultTemplate.getAlias());
        mapTemplate.setDefaultKey(defaultTemplate.getAlias());
        mapTemplate.setChosenKey(defaultTemplate.getAlias());

        // Extra info
        mapTemplate.setShow(MapTemplateModel::getAlias);
        mapTemplate.setGetExtraInfo(MapTemplateModel::getDescr);
        extraBlues.setExtraInfo("Extras are discarded before map building");
        extraReds.setExtraInfo("Extras are discarded before map building");

        // Emoji
        mulligans.setEmoji(CardEmojis.ExhaustedPlanet);
        extraBlues.setEmoji(TileEmojis.TileBlueBack);
        extraReds.setEmoji(TileEmojis.TileRedBack);
        mapTemplate.setEmoji(MiltyDraftEmojis.sliceA);

        // Load JSON if applicable
        if (json == null
                || !json.has("menuId")
                || !MENU_ID.equals(json.get("menuId").asText(""))) {
            return;
        }

        mulligans.initialize(json.get("mulligans"));
        extraBlues.initialize(json.get("extraBlues"));
        extraReds.initialize(json.get("extraReds"));
        mapTemplate.initialize(json.get("mapTemplate"));
        defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(players);
        mapTemplate.setDefaultKey(defaultTemplate.getAlias());
    }

    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        ls.add(mapTemplate);
        ls.add(mulligans);
        ls.add(extraBlues);
        ls.add(extraReds);
        return ls;
    }

    @Override
    protected void updateTransientSettings() {
        if (parent instanceof DraftSystemSettings dss) {
            int players = dss.getPlayerUserIds().size();
            Map<String, MapTemplateModel> allowed = Mapper.getMapTemplatesForPlayerCount(players).stream()
                    .filter(t -> !t.isNucleusTemplate())
                    .collect(Collectors.toMap(MapTemplateModel::getAlias, x -> x));
            MapTemplateModel defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(players);
            if (defaultTemplate != null) {
                mapTemplate.setAllValues(allowed, defaultTemplate.getAlias());
            }
        }
    }
}
