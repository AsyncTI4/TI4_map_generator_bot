package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.buttons.Buttons;
import ti4.helpers.MapTemplateHelper;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.SourceEmojis;

// This is a sub-menu
@Getter
@JsonIgnoreProperties({ "messageId", "bpp" })
public class GameSettings extends SettingsMenu {

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Submenus
    private final DeckSettings decks;
    // Settings
    private final IntegerSetting pointTotal;
    private final IntegerSetting stage1s;
    private final IntegerSetting stage2s;
    private final IntegerSetting secrets;
    private final BooleanSetting tigl;
    private final BooleanSetting alliance;
    private final ChoiceSetting<MapTemplateModel> mapTemplate;

    private int bpp;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------
    public GameSettings(Game game, JsonNode json, SettingsMenu parent) {
        super("game", "General Game Settings", "Adjust settings for the game such as point total", parent);

        // Initialize Settings to default values
        int defaultVP = game == null ? 10 : game.getVp();
        pointTotal = new IntegerSetting("Points", "Point Total", defaultVP, 1, 20, 1);
        stage1s = new IntegerSetting("Stage1s", "number of Stage 1 public objectives", 5, 1, 20, 1);
        stage2s = new IntegerSetting("Stage2s", "number of Stage 2 public objectives", 5, 1, 20, 1);
        secrets = new IntegerSetting("Secrets", "Max number of secret objectives", 3, 1, 10, 1);
        boolean defaultTigl = game != null && game.isCompetitiveTIGLGame();
        tigl = new BooleanSetting("TIGL", "TIGL Game", defaultTigl);
        alliance = new BooleanSetting("Alliance", "Alliance Mode", false);
        mapTemplate = new ChoiceSetting<>("Template", "Map Template", "6pStandard");

        // Emojis
        pointTotal.setEmoji(MiscEmojis.CustodiansVP);
        stage1s.setEmoji(CardEmojis.Public1);
        stage2s.setEmoji(CardEmojis.Public2);
        secrets.setEmoji(CardEmojis.SecretObjective);
        tigl.setEmoji(MiscEmojis.TIGL);
        alliance.setEmoji(SourceEmojis.StrategicAlliance);
        mapTemplate.setEmoji(MiltyDraftEmojis.sliceA);

        // Other initialization
        mapTemplate.setAllValues(Mapper.getMapTemplates().stream().collect(Collectors.toMap(MapTemplateModel::getAlias, x -> x)));
        mapTemplate.setShow(MapTemplateModel::getAlias);
        mapTemplate.setGetExtraInfo(MapTemplateModel::getDescr);

        // Get the correct JSON node for initialization if applicable.
        // Add additional names here to support new generated JSON as needed.
        if (json != null && json.has("gameSettings")) json = json.get("gameSettings");

        // Verify this is the correct JSON node and continue initialization
        List<String> historicIDs = new ArrayList<>(List.of("game"));
        if (json != null && json.has("menuId") && historicIDs.contains(json.get("menuId").asText(""))) {
            pointTotal.initialize(json.get("pointTotal"));
            stage1s.initialize(json.get("stage1s"));
            stage2s.initialize(json.get("stage2s"));
            secrets.initialize(json.get("secrets"));
            tigl.initialize(json.get("tigl"));
            alliance.initialize(json.get("alliance"));
            mapTemplate.initialize(json.get("mapTemplate"));
        }

        bpp = mapTemplate.getValue().bluePerPlayer();
        decks = new DeckSettings(json, this, Optional.ofNullable(game));
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    protected List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
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
    protected List<SettingsMenu> categories() {
        List<SettingsMenu> cats = new ArrayList<>();
        cats.add(decks);
        return cats;
    }

    @Override
    protected List<Button> specialButtons() {
        List<Button> buttons = new ArrayList<>();
        String prefix = menuAction + "_" + navId() + "_";
        buttons.add(Buttons.red(prefix + "preset14pt", "Long War (14pt)"));
        buttons.add(Buttons.red(prefix + "preset444", "4/4/4 mode"));
        return buttons;
    }

    @Override
    protected String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error = switch (action) {
            case "preset14pt" -> preset14vp();
            case "preset444" -> preset444();
            default -> null;
        };
        if (action.startsWith("changeTemplate_") && event instanceof StringSelectInteractionEvent sEvent) {
            afterChangeMapTemplateHandler(sEvent);
        }
        return (error == null ? "success" : error);
    }

    @Override
    protected void updateTransientSettings() {
        if (parent instanceof MiltySettings m) {
            int players = m.getPlayerSettings().getGamePlayers().getKeys().size();
            Map<String, MapTemplateModel> allowed = Mapper.getMapTemplatesForPlayerCount(players).stream()
                .collect(Collectors.toMap(MapTemplateModel::getAlias, x -> x));
            var defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(players);
            if (defaultTemplate == null) {
                return;
            }
            mapTemplate.setAllValues(allowed, defaultTemplate.getAlias());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Specific Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    private String preset444() {
        this.pointTotal.setVal(12);
        this.stage1s.setVal(4);
        this.stage2s.setVal(4);
        this.secrets.setVal(4);
        return null;
    }

    private String preset14vp() {
        this.pointTotal.setVal(14);
        this.stage1s.setVal(5);
        this.stage2s.setVal(5);
        this.secrets.setVal(3);
        return null;
    }

    private void afterChangeMapTemplateHandler(StringSelectInteractionEvent event) {
        FileUpload preview = null;
        if (parent != null && parent instanceof MiltySettings mparent)
            preview = MapTemplateHelper.generateTemplatePreviewImage(event, mparent.getGame(), mapTemplate.getValue());
        if (preview != null)
            event.getHook().sendMessage("Here is a preview of the selected map template:")
                .addFiles(preview).setEphemeral(true).queue();
        if (mapTemplate.getValue().bluePerPlayer() != bpp && parent instanceof MiltySettings m) {
            SliceGenerationSettings slice = m.getSliceSettings();
            bpp = mapTemplate.getValue().bluePerPlayer();
            if (bpp == 3) {
                slice.getMinimumRes().setVal(2);
                slice.getMinimumInf().setVal(3);
                slice.getTotalValue().setValLow(9);
                slice.getTotalValue().setValHigh(13);
            } else if (bpp == 2) {
                slice.getMinimumRes().setVal(1);
                slice.getMinimumInf().setVal(1);
                slice.getTotalValue().setValLow(5);
                slice.getTotalValue().setValHigh(8);
            } else if (bpp == 1) {
                slice.getMinimumRes().setVal(0);
                slice.getMinimumInf().setVal(0);
                slice.getTotalValue().setValLow(0);
                slice.getTotalValue().setValHigh(6);
            }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "The number of blue tiles per player in the map template changed, your slice settings have been reset to accomodate the change.");
        }
    }
}
