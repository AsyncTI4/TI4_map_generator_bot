package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.helpers.settingsFramework.settings.*;
import ti4.map.Game;
import ti4.model.MapTemplateModel;

// This is a sub-menu
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GameSettings extends SettingsMenu {

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Submenus
    private DeckSettings decks;
    // Settings
    private IntegerSetting pointTotal, stage1s, stage2s, secrets;
    private BooleanSetting tigl, alliance;
    private ChoiceSetting<MapTemplateModel> mapTemplate;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    public void finishInitialization(Game game, SettingsMenu parent) {
        this.menuId = "game";
        this.menuName = "General Game Settings";
        this.description = "Adjust settings for the game such as point total";

        // finish initializing
        if (decks == null) decks = new DeckSettings();

        // Initialize defaults, including any values loaded from JSON
        pointTotal = new IntegerSetting("Points", "Point Total", 10, 1, 20, 1, pointTotal);
        stage1s = new IntegerSetting("Stage1s", "# of Stage 1's", 5, 1, 20, 1, stage1s);
        stage2s = new IntegerSetting("Stage2s", "# of Stage 2's", 5, 1, 20, 1, stage2s);
        secrets = new IntegerSetting("Secrets", "Max # of Secrets", 3, 1, 10, 1, secrets);
        tigl = new BooleanSetting("TIGL", "TIGL Game", false, tigl);
        alliance = new BooleanSetting("Alliance", "Alliance Mode", false, alliance);
        mapTemplate = new ChoiceSetting<>("Template", "Map Template", "6pStandard", mapTemplate);

        // initialize other data
        pointTotal.setEmoji(Emojis.CustodiansVP);
        stage1s.setEmoji(Emojis.Public1);
        stage2s.setEmoji(Emojis.Public2);
        secrets.setEmoji(Emojis.SecretObjective);
        tigl.setEmoji(Emojis.TIGL);
        alliance.setEmoji(Emojis.StrategicAlliance);

        mapTemplate.setEmoji(Emojis.sliceA);
        mapTemplate.setAllValues(Mapper.getMapTemplates().stream().collect(Collectors.toMap(MapTemplateModel::getAlias, x -> x)));
        mapTemplate.setShow(MapTemplateModel::getAlias);

        super.finishInitialization(game, parent);
    }

    @Override
    public List<SettingInterface> settings() {
        updateTransientSettings();

        List<SettingInterface> ls = new ArrayList<SettingInterface>();
        ls.add(pointTotal);
        ls.add(stage1s);
        ls.add(stage2s);
        ls.add(secrets);
        ls.add(tigl);
        ls.add(alliance);
        ls.add(mapTemplate);
        return ls;
    }

    @Override
    public List<SettingsMenu> categories() {
        List<SettingsMenu> cats = new ArrayList<>();
        cats.add(decks);
        return cats;
    }

    @Override
    public List<Button> specialButtons() {
        List<Button> buttons = new ArrayList<>();
        String prefix = menuAction + "_" + navId() + "_";
        buttons.add(Button.danger(prefix + "preset14pt", "Long War (14pt)"));
        buttons.add(Button.danger(prefix + "preset444", "4/4/4 mode"));
        return buttons;
    }

    @Override
    public String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error = switch (action) {
            case "preset14pt" -> preset14vp();
            case "preset444" -> preset444();
            default -> null;
        };
        return (error == null ? "success" : error);
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Specific Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    private void updateTransientSettings() {
        if (parent instanceof MiltySettings m) {
            int players = m.getPlayerSettings().getGamePlayers().getKeys().size();
            Map<String, MapTemplateModel> allowed = Mapper.getMapTemplatesForPlayerCount(players).stream()
                .collect(Collectors.toMap(MapTemplateModel::getAlias, x -> x));
            String defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(players).getAlias();
            mapTemplate.setAllValues(allowed, defaultTemplate);
        }
    }

    private String preset444() {
        this.pointTotal.val = 12;
        this.stage1s.val = 4;
        this.stage2s.val = 4;
        this.secrets.val = 4;
        return null;
    }

    private String preset14vp() {
        this.pointTotal.val = 14;
        this.stage1s.val = 5;
        this.stage2s.val = 5;
        this.secrets.val = 3;
        return null;
    }
}
