package ti4.spring.service.deploy;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

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
}
