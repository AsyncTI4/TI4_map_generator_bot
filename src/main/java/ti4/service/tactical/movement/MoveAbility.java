package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public interface MoveAbility {
    boolean enabled(MoveContext ctx);

    void contribute(MoveContext ctx, List<Button> buttons);
}
