package ti4.spring.service.deploy;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import ti4.spring.context.SpringContext;

@Service
public class InstanceActivityService {

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean draining = new AtomicBoolean(false);

    public boolean isActive() {
        return active.get();
    }

    public void setActive(boolean active) {
        this.active.set(active);
        if (!active) {
            draining.set(false);
        }
    }

    public boolean isDraining() {
        return draining.get();
    }

    public void setDraining(boolean draining) {
        this.draining.set(draining);
    }

    public boolean shouldHandleDiscordInteraction() {
        return isActive() && !isDraining();
    }

    /**
     * Returns whether the current process should handle a Discord interaction, defaulting to false when Spring is not
     * initialized.
     */
    public static boolean shouldHandleCurrentProcessInteraction() {
        try {
            return SpringContext.getBean(InstanceActivityService.class).shouldHandleDiscordInteraction();
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
