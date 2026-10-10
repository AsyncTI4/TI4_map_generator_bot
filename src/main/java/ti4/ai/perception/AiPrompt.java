package ti4.ai.perception;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public record AiPrompt(
        String channelId,
        String messageId,
        PromptSource source,
        String content,
        List<PromptButton> buttons,
        long createdAtMillis) {

    public enum PromptSource {
        AI_THREAD,
        PUBLIC,
        COMBAT_THREAD
    }

    public boolean isHidden() {
        return source == PromptSource.AI_THREAD;
    }

    public String key() {
        return channelId + "/" + messageId;
    }

    public Optional<PromptButton> firstEnabled(Predicate<PromptButton> predicate) {
        return buttons.stream()
                .filter(button -> !button.disabled())
                .filter(predicate)
                .findFirst();
    }

    public Optional<PromptButton> enabledHandler(String handlerId) {
        return firstEnabled(button -> button.handlerId().equals(handlerId));
    }

    public Optional<PromptButton> enabledHandlerPrefix(String prefix) {
        return firstEnabled(button -> button.handlerId().startsWith(prefix));
    }

    public List<PromptButton> enabledButtons() {
        return buttons.stream().filter(button -> !button.disabled()).toList();
    }

    public boolean hasHandlerPrefix(String prefix) {
        return buttons.stream().anyMatch(button -> button.handlerId().startsWith(prefix));
    }
}
