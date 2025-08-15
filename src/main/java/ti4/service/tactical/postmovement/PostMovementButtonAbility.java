package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public interface PostMovementButtonAbility {
    boolean enabled(PostMovementButtonContext ctx);

    void contribute(PostMovementButtonContext ctx, List<Button> buttons);
}
