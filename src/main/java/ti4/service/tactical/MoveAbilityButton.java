package ti4.service.tactical;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public interface MoveAbilityButton {
    boolean enabled(MoveContext ctx);

    List<Button> build(MoveContext ctx);
}
