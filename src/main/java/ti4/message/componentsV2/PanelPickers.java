package ti4.message.componentsV2;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.DefaultValue;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class PanelPickers {

    public static EntitySelectMenu users(
            String id, String placeholder, Collection<String> selectedIds, int min, int max) {
        return builder(id, SelectTarget.USER, placeholder, selectedIds, DefaultValue::user, min, max)
                .build();
    }

    public static EntitySelectMenu roles(
            String id, String placeholder, Collection<String> selectedIds, int min, int max) {
        return builder(id, SelectTarget.ROLE, placeholder, selectedIds, DefaultValue::role, min, max)
                .build();
    }

    public static EntitySelectMenu channels(
            String id, String placeholder, Collection<String> selectedIds, int min, int max, ChannelType... types) {
        EntitySelectMenu.Builder builder =
                builder(id, SelectTarget.CHANNEL, placeholder, selectedIds, DefaultValue::channel, min, max);
        if (types.length > 0) builder.setChannelTypes(types);
        return builder.build();
    }

    public static Label inForm(String label, EntitySelectMenu menu) {
        return Label.of(label, optionalWhenEmptyAllowed(menu));
    }

    public static Label inForm(String label, String hint, EntitySelectMenu menu) {
        return Label.of(label, hint, optionalWhenEmptyAllowed(menu));
    }

    public static List<String> picked(ModalInteractionEvent event, String field) {
        ModalMapping mapping = event.getValue(field);
        return mapping == null ? List.of() : mapping.getAsStringList();
    }

    public static List<String> picked(EntitySelectInteractionEvent event) {
        return event.getValues().stream()
                .map(mentionable -> mentionable.getId())
                .toList();
    }

    private static EntitySelectMenu optionalWhenEmptyAllowed(EntitySelectMenu menu) {
        if (menu.getMinValues() > 0) return menu;
        return menu.createCopy().setRequired(false).build();
    }

    private static EntitySelectMenu.Builder builder(
            String id,
            SelectTarget target,
            String placeholder,
            Collection<String> selectedIds,
            Function<String, DefaultValue> toDefault,
            int min,
            int max) {
        EntitySelectMenu.Builder builder = EntitySelectMenu.create(id, target).setRequiredRange(min, max);
        if (StringUtils.isNotBlank(placeholder)) builder.setPlaceholder(placeholder);
        if (!selectedIds.isEmpty()) {
            builder.setDefaultValues(selectedIds.stream().map(toDefault).toList());
        }
        return builder;
    }
}
