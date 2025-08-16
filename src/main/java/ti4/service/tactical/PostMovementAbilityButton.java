package ti4.service.tactical;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public interface PostMovementAbilityButton {
    boolean enabled(PostMovementButtonContext ctx);

    List<Button> build(PostMovementButtonContext ctx);
}


