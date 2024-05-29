package ti4.helpers.settingsFramework.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
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
import ti4.message.BotLogger;

@AllArgsConstructor
public class ChoiceSetting<T> extends SettingInterface {
    // Will show up in json
    @Getter
    private String chosenKey;
    // Will not show up in json
    @Getter
    @JsonIgnore
    private Map<String, T> allValues;
    @Getter
    @JsonIgnore
    private String defaultKey;
    @Setter
    private Function<T, String> show;
    @Setter
    private Function<T, String> getEmoji;
    private String lang = "pick";

    public ChoiceSetting() {
        this.allValues = new HashMap<>();
    }

    public ChoiceSetting(String id, String name, String defaultKey, ChoiceSetting<T> choice) {
        this.chosenKey = choice == null ? defaultKey : choice.chosenKey;

        this.id = id;
        this.name = name;
        this.defaultKey = defaultKey;
        this.allValues = new HashMap<>();
    }

    @JsonIgnore
    public T getValue() {
        return allValues.get(chosenKey);
    }

    // Abstract methods
    public String modify(GenericInteractionCreateEvent event, String action) {
        if (action.startsWith("change" + id)) return changeValue(event, action);
        return "[invalid action: " + action + "]";
    }

    protected List<Button> buttons(String idPrefix) {
        List<Button> ls = new ArrayList<>();
        Button choose = Button.secondary(idPrefix + "change" + id, lang + " " + this.name);
        if (this.emoji != null) choose = choose.withEmoji(Emoji.fromFormatted(this.emoji));
        ls.add(choose);
        return ls;
    }

    public void reset() {
        chosenKey = defaultKey;
    }

    public String shortValue() {
        return show.apply(allValues.get(chosenKey));
    }

    public String longValue() {
        return show.apply(allValues.get(chosenKey));
    }

    // Helper methods
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
            String itemToChoose = null;
            if (values.size() == 1) {
                itemToChoose = values.get(0);
            } else {
                return "Too many values were selected";
            }

            if (allValues.containsKey(itemToChoose)) {
                chosenKey = itemToChoose;
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

    public void setAllValues(Map<String, T> values) {
        allValues.clear();
        for (Map.Entry<String, T> entry : values.entrySet()) {
            allValues.put(entry.getKey(), entry.getValue());
        }
        // if the chosen/default keys no longer exist, replace with some random thing in the list I guess, idk
        if (!allValues.containsKey(defaultKey)) {
            chosenKey = new ArrayList<>(allValues.keySet()).get(0);
            defaultKey = chosenKey;
        }
    }

    public void setAllValues(Map<String, T> values, String newDefault) {
        allValues.clear();
        for (Map.Entry<String, T> entry : values.entrySet()) {
            allValues.put(entry.getKey(), entry.getValue());
        }
        // if the chosen/default keys no longer exist, replace with some random thing in the list I guess, idk
        if (!allValues.containsKey(chosenKey)) {
            chosenKey = newDefault;
        }
        if (!allValues.containsKey(defaultKey)) {
            defaultKey = newDefault;
        }
    }
}
