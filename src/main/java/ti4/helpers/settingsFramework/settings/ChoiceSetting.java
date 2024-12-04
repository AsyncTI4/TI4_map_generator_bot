package ti4.helpers.settingsFramework.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.message.BotLogger;

@Getter
@Setter
@JsonIncludeProperties({ "id", "chosenKey" })
public class ChoiceSetting<T> extends SettingInterface {
    private String chosenKey;
    private String defaultKey;
    private Map<String, T> allValues;
    private Function<T, String> show;
    private Function<T, String> getEmoji;
    private Function<T, String> getExtraInfo;
    private String lang = "pick";

    public ChoiceSetting(String id, String name, String defaultKey) {
        super(id, name);

        this.chosenKey = defaultKey;
        this.defaultKey = defaultKey;
        this.allValues = new HashMap<>();
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Abstract Methods
    // ---------------------------------------------------------------------------------------------------------------------------------
    protected void init(JsonNode json) {
        if (json.has("chosenKey")) chosenKey = json.get("chosenKey").asText(chosenKey);
    }

    public String modify(GenericInteractionCreateEvent event, String action) {
        if (action.startsWith("change" + id)) return changeValue(event, action);
        return "[invalid action: " + action + "]";
    }

    public void reset() {
        chosenKey = defaultKey;
    }

    protected String shortValue() {
        return show.apply(allValues.get(chosenKey));
    }

    protected String longValue() {
        return show.apply(allValues.get(chosenKey));
    }

    protected List<Button> buttons(String idPrefix) {
        List<Button> ls = new ArrayList<>();
        Button choose = Buttons.gray(idPrefix + "change" + id, lang + " " + this.name);
        if (this.emoji != null) choose = choose.withEmoji(Emoji.fromFormatted(this.emoji));
        ls.add(choose);
        return ls;
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------------------------------------------------------------
    public T getValue() {
        return allValues.get(chosenKey);
    }

    public void setAllValues(Map<String, T> values) {
        allValues.clear();
        allValues.putAll(values);
        // if the chosen/default keys no longer exist, replace with some random thing in the list I guess, idk
        if (!allValues.containsKey(defaultKey) && !allValues.isEmpty()) {
            chosenKey = new ArrayList<>(allValues.keySet()).getFirst();
            defaultKey = chosenKey;
        }
    }

    public void setAllValues(Map<String, T> values, String newDefault) {
        allValues.clear();
        allValues.putAll(values);
        // if the chosen/default keys no longer exist, replace with some random thing in the list I guess, idk
        if (!allValues.containsKey(chosenKey)) {
            chosenKey = newDefault;
        }
        if (!allValues.containsKey(defaultKey)) {
            defaultKey = newDefault;
        }
    }

    private String changeValue(GenericInteractionCreateEvent event, String action) {
        String suffix = action.replace("change" + id, "");
        List<Map.Entry<String, T>> items = new ArrayList<>(allValues.entrySet());
        if (StringUtils.isBlank(suffix)) {
            if (event instanceof ButtonInteractionEvent buttonEvent) {
                sendSelectionBoxes(buttonEvent, items);
                return null;
            }
            return "Could not complete action";
        } else if (event instanceof StringSelectInteractionEvent selectEvent) {
            List<String> values = selectEvent.getValues();
            String itemToChoose;
            if (values.size() == 1) {
                itemToChoose = values.getFirst();
            } else {
                return "Too many values were selected";
            }

            if (allValues.containsKey(itemToChoose)) {
                chosenKey = itemToChoose;
                if (getGetExtraInfo() != null) setExtraInfo(getGetExtraInfo().apply(getValue()));
                return null;
            }
            return itemToChoose + " is not an allowed value";
        } else {
            return "Could not complete action. Invalid event?";
        }
    }

    private void sendSelectionBoxes(ButtonInteractionEvent buttonEvent, List<Map.Entry<String, T>> entries) {
        String prefixID = buttonEvent.getButton().getId();
        List<ActionRow> rows = new ArrayList<>();
        int x = 0;
        for (List<Map.Entry<String, T>> menu : ListUtils.partition(entries, 25)) {
            List<SelectOption> options = menu.stream()
                .map(entry -> SelectOption.of(show.apply(entry.getValue()), entry.getKey()))
                .toList();
            StringSelectMenu selectionMenu = StringSelectMenu.create(prefixID + "_" + x)
                .addOptions(options)
                .setPlaceholder("Select an option")
                .setRequiredRange(1, 1)
                .build();
            rows.add(ActionRow.of(selectionMenu));
            x++;
        }
        buttonEvent.getHook().sendMessage("Select a new setting for " + name)
            .addComponents(rows)
            .setEphemeral(true)
            .queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
