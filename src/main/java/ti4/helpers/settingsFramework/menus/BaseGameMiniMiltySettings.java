package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.NotNull;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.helpers.settingsFramework.settings.ReadOnlyTextSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;
import ti4.service.draft.BaseGameMiniMiltyService;
import tools.jackson.databind.JsonNode;

@Getter
@JsonIgnoreProperties({"messageId", "mapTemplateStatus", "customMapStatus"})
public class BaseGameMiniMiltySettings extends SettingsMenu {
    private static final String MENU_ID = "baseGameMiniMilty";

    private final ReadOnlyTextSetting mapTemplateStatus;
    private final ReadOnlyTextSetting customMapStatus;
    private final BaseGameMiniMiltyFactionSettings factionSettings;

    @JsonIgnore
    private final Game game;

    private String customMapString;

    public BaseGameMiniMiltySettings(@NotNull Game game, JsonNode json) {
        super(MENU_ID, "Base Game Mini-Milty", "Set up a standalone base-game faction and speaker-order draft.", null);
        this.game = game;

        mapTemplateStatus = new ReadOnlyTextSetting("Template", "Map Template");
        customMapStatus = new ReadOnlyTextSetting("CustomMap", "Custom Map String");

        if (json != null
                && json.has("menuId")
                && MENU_ID.equals(json.get("menuId").asText(""))) {
            customMapString = json.hasNonNull("customMapString")
                    ? json.get("customMapString").asText(null)
                    : null;
        }

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
        return List.of(mapTemplateStatus, customMapStatus);
    }

    @Override
    protected List<Button> specialButtons() {
        String prefix = menuAction + "_" + navId() + "_";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue(prefix + "customMapString~MDL", "Insert Map String"));
        if (hasCustomMapString()) {
            buttons.add(Buttons.red(prefix + "clearCustomMapString", "Clear Map String"));
        }
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
            case "customMapString~MDL" -> {
                String error = handleCustomMapString(event);
                yield error == null ? "success" : error;
            }
            case "customMapString" -> {
                String error = handleCustomMapString(event);
                yield error == null ? "success" : error;
            }
            case "clearCustomMapString" -> {
                clearCustomMapString();
                yield "success";
            }
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
        customMapStatus.setDisplay(
                hasCustomMapString() ? "Stored (" + customMapString.length() + " chars)" : "Not set");
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
        MapTemplateModel template =
                Mapper.getDefaultMapTemplateForPlayerCount(getPlayerUserIds().size());
        if (template == null || template.isNucleusTemplate()) {
            return null;
        }
        if (template.bluePerPlayer() != 3 || template.redPerPlayer() != 2) {
            return null;
        }
        return template;
    }

    public boolean hasCustomMapString() {
        return customMapString != null && !customMapString.isBlank();
    }

    public void setCustomMapString(String customMapString) {
        this.customMapString = customMapString;
    }

    private String clearCustomMapString() {
        customMapString = null;
        return null;
    }

    private String handleCustomMapString(GenericInteractionCreateEvent event) {
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            Modal modal = BaseGameMiniMiltyService.buildMapStringModal(game, navId(), customMapString);
            buttonEvent.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
            return null;
        }
        if (event instanceof ModalInteractionEvent modalEvent) {
            return BaseGameMiniMiltyService.applyMapStringFromModal(modalEvent, this);
        }
        return "Unknown event type";
    }

    private static String menuNotes() {
        return """


            **Notes:**
            > When the draft is complete, please manually remove the wormhole nexus.

            > If you don't use a custom map string, the draft will try to generate a balanced map.""";
    }
}
