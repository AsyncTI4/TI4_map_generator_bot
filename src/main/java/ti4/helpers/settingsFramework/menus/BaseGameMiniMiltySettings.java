package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.jetbrains.annotations.NotNull;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.helpers.settingsFramework.settings.ReadOnlyTextSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;
import ti4.service.draft.BaseGameMiniMiltyService;
import tools.jackson.databind.JsonNode;

@Getter
@JsonIgnoreProperties("messageId")
public class BaseGameMiniMiltySettings extends SettingsMenu {
    private static final String MENU_ID = "baseGameMiniMilty";

    private final ReadOnlyTextSetting mapTemplateStatus;
    private final BaseGameMiniMiltyFactionSettings factionSettings;

    @JsonIgnore
    private final Game game;

    public BaseGameMiniMiltySettings(@NotNull Game game, JsonNode json) {
        super(MENU_ID, "Base Game Mini-Milty", "Set up a standalone base-game faction and speaker-order draft.", null);
        this.game = game;

        mapTemplateStatus = new ReadOnlyTextSetting("Template", "Map Template");

        factionSettings =
                new BaseGameMiniMiltyFactionSettings(game, json != null ? json.get("factionSettings") : null, this);

        if (json != null && json.has("messageId")) {
            setMessageId(json.get("messageId").asText(null));
        }
        updateTransientSettings();
    }

    @Override
    protected List<SettingsMenu> categories() {
        return List.of(factionSettings);
    }

    @Override
    protected List<SettingInterface> settings() {
        return List.of(mapTemplateStatus);
    }

    @Override
    protected List<Button> specialButtons() {
        String prefix = menuAction + "_" + navId() + "_";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(prefix + "startMiniMilty", "Start Mini-Milty!"));
        return buttons;
    }

    @Override
    public String menuSummaryString(String lastSettingTouched) {
        return super.menuSummaryString(lastSettingTouched) + menuNotes();
    }

    @Override
    protected String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        return switch (action) {
            case "startMiniMilty" -> {
                String error = BaseGameMiniMiltyService.startFromSettings(event, this);
                yield error == null ? "success" : error;
            }
            default -> null;
        };
    }

    @Override
    protected void updateTransientSettings() {
        MapTemplateModel template = getResolvedMapTemplate();
        mapTemplateStatus.setDisplay(template == null ? "No standard template available" : template.getAlias());
    }

    public static boolean isBaseGameMiniMiltyMenuComponent(String componentId) {
        return componentId != null
                && (componentId.contains("_" + MENU_ID + "_") || componentId.contains("_" + MENU_ID + "."));
    }

    public List<String> getPlayerUserIds() {
        return new ArrayList<>(game.getPlayerIDs());
    }

    public List<ComponentSource> getFactionSources() {
        return List.of(ComponentSource.base);
    }

    public MapTemplateModel getResolvedMapTemplate() {
        MapTemplateModel template = null;
        if (game.getMapTemplateID() != null
                && !game.getMapTemplateID().isBlank()
                && !"null".equalsIgnoreCase(game.getMapTemplateID())) {
            template = Mapper.getMapTemplate(game.getMapTemplateID());
        }
        if (template == null) {
            template = Mapper.getDefaultMapTemplateForPlayerCount(
                    getPlayerUserIds().size());
        }
        if (template == null || template.isNucleusTemplate()) {
            return null;
        }
        if (template.getPlayerCount() != null
                && template.getPlayerCount() != getPlayerUserIds().size()) {
            return null;
        }
        if (template.bluePerPlayer() != 3 || template.redPerPlayer() != 2) {
            return null;
        }
        return template;
    }

    private static String menuNotes() {
        return """


            **Notes:**
            > When the draft is complete, please manually remove the wormhole nexus.

            > Custom map strings are not supported through this draft menu. To use a custom map, run `/map add_tile_list` after the draft. This will remove home systems and starting fleets, so those will have to be re-added manually as well. Apologies for the inconvenience.""";
    }
}
