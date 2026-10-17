package ti4.ai.perception;

import javax.annotation.Nullable;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.routing.ComponentIdEnvelope;

public record PromptButton(
        int index,
        String customId,
        String handlerId,
        @Nullable String ownerFaction,
        String label,
        boolean disabled) {

    public static PromptButton of(int index, Button button) {
        ComponentIdEnvelope envelope = ComponentIdEnvelope.decode(button.getCustomId());
        String handlerId = envelope.handlerId() == null ? "" : envelope.handlerId();
        String owner = envelope.ownerFaction() != null ? envelope.ownerFaction() : envelope.spoofedFaction();
        return new PromptButton(index, button.getCustomId(), handlerId, owner, button.getLabel(), button.isDisabled());
    }

    public boolean isOwnedBy(String faction) {
        return faction.equals(ownerFaction);
    }

    public boolean isUnowned() {
        return ownerFaction == null;
    }
}
