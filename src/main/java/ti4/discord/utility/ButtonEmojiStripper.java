package ti4.discord.utility;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

@UtilityClass
public class ButtonEmojiStripper {

    public static boolean hasButtonEmoji(MessageCreateData message) {
        return message.getComponents().stream()
                .filter(ActionRow.class::isInstance)
                .map(ActionRow.class::cast)
                .flatMap(row -> row.getComponents().stream())
                .anyMatch(child -> child instanceof Button button && button.getEmoji() != null);
    }

    public static MessageCreateData withoutButtonEmoji(MessageCreateData message) {
        List<MessageTopLevelComponent> components = new ArrayList<>();
        for (MessageTopLevelComponent component : message.getComponents()) {
            components.add(component instanceof ActionRow row ? withoutButtonEmoji(row) : component);
        }
        return MessageCreateBuilder.from(message).setComponents(components).build();
    }

    private static ActionRow withoutButtonEmoji(ActionRow row) {
        List<ActionRowChildComponent> children = new ArrayList<>();
        for (ActionRowChildComponent child : row.getComponents()) {
            children.add(child instanceof Button button ? withoutEmoji(button) : child);
        }
        return ActionRow.of(children);
    }

    private static Button withoutEmoji(Button button) {
        if (button.getEmoji() == null) {
            return button;
        }
        Button labelled = button.getLabel().isBlank() ? button.withLabel(" ") : button;
        return labelled.withEmoji(null);
    }
}
