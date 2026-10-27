package ti4.discord.utility;

import static org.assertj.core.api.Assertions.assertThat;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.Test;

class ButtonEmojiStripperTest {

    @Test
    void stripsEmojiButKeepsLabelIdAndStyle() {
        Button withEmoji = Button.secondary("forceARefresh_sol", "Sol").withEmoji(Emoji.fromUnicode("🐿️"));
        Button plain = Button.danger("deleteButtons", "Done Resolving");
        MessageCreateData message = new MessageCreateBuilder()
                .setContent("hello")
                .setComponents(ActionRow.of(withEmoji, plain))
                .build();

        assertThat(ButtonEmojiStripper.hasButtonEmoji(message)).isTrue();

        MessageCreateData stripped = ButtonEmojiStripper.withoutButtonEmoji(message);

        assertThat(ButtonEmojiStripper.hasButtonEmoji(stripped)).isFalse();
        assertThat(stripped.getContent()).isEqualTo("hello");
        ActionRow row = (ActionRow) stripped.getComponents().getFirst();
        Button first = (Button) row.getComponents().get(0);
        assertThat(first.getCustomId()).isEqualTo("forceARefresh_sol");
        assertThat(first.getLabel()).isEqualTo("Sol");
        assertThat(first.getStyle()).isEqualTo(withEmoji.getStyle());
        assertThat(row.getComponents().get(1)).isEqualTo(plain);
    }

    @Test
    void emojiOnlyButtonGetsPlaceholderLabel() {
        Button emojiOnly = Button.primary("id", Emoji.fromUnicode("🐿️"));
        MessageCreateData message = new MessageCreateBuilder()
                .setComponents(ActionRow.of(emojiOnly))
                .build();

        MessageCreateData stripped = ButtonEmojiStripper.withoutButtonEmoji(message);

        assertThat(ButtonEmojiStripper.hasButtonEmoji(stripped)).isFalse();
    }
}
