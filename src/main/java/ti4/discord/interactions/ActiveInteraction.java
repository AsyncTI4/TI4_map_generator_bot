package ti4.discord.interactions;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

@UtilityClass
public class ActiveInteraction {

    private static final ThreadLocal<IReplyCallback> ACTIVE = new ThreadLocal<>();

    public static void runWith(IReplyCallback interaction, Runnable action) {
        IReplyCallback previous = ACTIVE.get();
        ACTIVE.set(interaction);
        try {
            action.run();
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    public static Optional<IReplyCallback> pressedBy(String userId) {
        IReplyCallback interaction = ACTIVE.get();
        if (interaction == null || userId == null || !interaction.isAcknowledged()) {
            return Optional.empty();
        }
        return userId.equals(interaction.getUser().getId()) ? Optional.of(interaction) : Optional.empty();
    }
}
