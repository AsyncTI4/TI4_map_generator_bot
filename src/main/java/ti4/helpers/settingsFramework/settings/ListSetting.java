package ti4.helpers.settingsFramework.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import ti4.message.BotLogger;

@AllArgsConstructor
public class ListSetting<T> extends SettingInterface {
    // Will show up in json
    @Getter
    private Set<String> keys;
    // Will not show up in json
    @Getter
    @JsonIgnore
    private Map<String, T> allValues;
    @Getter
    @JsonIgnore
    private Set<String> defaultKeys;
    @Setter
    @JsonIgnore
    private Function<T, String> show;
    @Setter
    @JsonIgnore
    private Function<T, String> getEmoji;
    private String includeLang = "include";
    private String removeLang = "remove";

    public ListSetting() {
        this.allValues = new HashMap<>();
        this.defaultKeys = new HashSet<>();
        this.keys = new HashSet<>();
    }

    public ListSetting(String id, String name, String include, String remove, Set<Entry<String, T>> allVals, Set<String> values, Set<String> defaults) {
        this.allValues = new HashMap<>();
        if (allVals != null) {
            for (Map.Entry<String, T> entry : allVals) {
                allValues.put(entry.getKey(), entry.getValue());
            }
        }

        this.defaultKeys = new HashSet<>();
        if (defaults != null) defaultKeys.addAll(defaults);

        this.keys = new HashSet<>();
        if (values != null) this.keys.addAll(values);

        this.id = id;
        this.name = name;
        this.includeLang = include;
        this.removeLang = remove;
    }

    // Abstract methods
    public String modify(GenericInteractionCreateEvent event, String action) {
        if (action.startsWith("include" + id)) return includeValue(event, action);
        if (action.startsWith("remove" + id)) return removeValue(event, action);
        return "[invalid action: " + action + "]";
    }

    protected List<Button> buttons(String idPrefix) {
        Button include = Button.success(idPrefix + "include" + id, includeLang).withEmoji(emojiUp);
        Button remove = Button.danger(idPrefix + "remove" + id, removeLang).withEmoji(emojiDown);
        List<Button> ls = new ArrayList<>();
        if (!keys.isEmpty()) ls.add(remove);
        if (!(keys.size() == allValues.size())) ls.add(include);
        return ls;
    }

    public void reset() {
        keys = new HashSet<>(defaultKeys);
    }

    public String shortValue() {
        List<String> values = keys.stream().sorted()
            .map(k -> show.apply(allValues.get(k))).toList();
        return "[" + String.join(",", values) + "]";
    }

    public String longValue() {
        List<String> values = keys.stream().sorted()
            .map(k -> show.apply(allValues.get(k))).toList();
        return "[" + String.join(",", values) + "]";
    }

    // Helper methods
    private String includeValue(GenericInteractionCreateEvent event, String action) {
        String suffix = action.replace("include" + id, "");
        List<Map.Entry<String, T>> items = new ArrayList<>(allValues.entrySet().stream().filter(e -> !keys.contains(e.getKey())).toList());
        if (StringUtils.isBlank(suffix)) {
            if (event instanceof ButtonInteractionEvent buttonEvent) {
                sendSelectionBoxes(buttonEvent, items, " include in ");
                return null;
            }
            return "Could not complete action";
        } else if (event instanceof StringSelectInteractionEvent selectEvent) {
            List<String> itemsToAdd = selectEvent.getValues();
            if (!CollectionUtils.containsAll(keys, itemsToAdd)) {
                keys.addAll(itemsToAdd);
                return null;
            } else {
                return "The items [" + String.join(",", itemsToAdd) + "] are already in the list.";
            }
        } else {
            return "Could not complete action. Invalid event?";
        }
    }

    private String removeValue(GenericInteractionCreateEvent event, String action) {
        String suffix = action.replace("remove" + id, "");
        List<Map.Entry<String, T>> items = new ArrayList<>(allValues.entrySet().stream().filter(e -> keys.contains(e.getKey())).toList());
        if (StringUtils.isBlank(suffix)) {
            if (event instanceof ButtonInteractionEvent buttonEvent) {
                sendSelectionBoxes(buttonEvent, items, " remove from ");
                return null;
            }
            return "Could not complete action";
        } else if (event instanceof StringSelectInteractionEvent selectEvent) {
            List<String> itemsToAdd = selectEvent.getValues();
            if (CollectionUtils.containsAny(keys, itemsToAdd)) {
                keys.removeAll(itemsToAdd);
                return null;
            } else {
                return "The items [" + String.join(",", itemsToAdd) + "] are not in the list.";
            }
        } else {
            return "Could not complete action. Invalid event?";
        }
    }

    private void sendSelectionBoxes(ButtonInteractionEvent buttonEvent, List<Map.Entry<String, T>> entries, String lang) {
        String prefixID = buttonEvent.getButton().getId();
        List<ActionRow> rows = new ArrayList<>();
        int x = 0;
        Collections.sort(entries, Comparator.comparing(e -> show.apply(e.getValue())));
        for (List<Map.Entry<String, T>> menu : ListUtils.partition(entries, 25)) {
            List<SelectOption> options = menu.stream()
                .map(entry -> SelectOption.of(show.apply(entry.getValue()), entry.getKey()))
                .toList();
            char start = options.get(0).getLabel().charAt(0);
            char end = options.get(options.size() - 1).getLabel().charAt(0);
            String placeholder = String.format("Select [%s-%s]", start, end);
            StringSelectMenu selectionMenu = StringSelectMenu.create(prefixID + "_" + x)
                .addOptions(options)
                .setPlaceholder(placeholder)
                .setRequiredRange(0, menu.size())
                .build();
            rows.add(ActionRow.of(selectionMenu));
            x++;
        }
        buttonEvent.getHook().sendMessage("Choose an item to" + lang + name)
            .addComponents(rows)
            .setEphemeral(true)
            .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public void setAllValues(Map<String, T> values) {
        allValues.clear();
        for (Map.Entry<String, T> entry : values.entrySet()) {
            allValues.put(entry.getKey(), entry.getValue());
        }
        // remove keys that no longer exist
        keys = new HashSet<>(keys.stream().filter(k -> allValues.containsKey(k)).toList());
        defaultKeys = new HashSet<>(defaultKeys.stream().filter(k -> allValues.containsKey(k)).toList());
    }

    public void setKeys(List<String> keys) {
        if (allValues == null || allValues.isEmpty()) {
            // technically, this is bad, but it's just a workaround
            this.keys.addAll(keys);
            return;
        }
        // otherwise don't include keys that have no value
        this.keys = new HashSet<>(keys.stream().filter(k -> allValues.containsKey(k)).toList());
    }

    public void setDefaultKeys(List<String> defaultKeys) {
        if (allValues == null || allValues.isEmpty()) {
            // technically, this is bad, but it's just a workaround
            this.defaultKeys.addAll(defaultKeys);
            return;
        }
        // otherwise don't include keys that have no value
        this.defaultKeys = new HashSet<>(defaultKeys.stream().filter(k -> allValues.containsKey(k)).toList());
    }
}
