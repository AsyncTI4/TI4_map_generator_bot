package ti4.service.tactical;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public interface PlanetAbilityButton {
    boolean enabled(LandingContext ctx);

    List<Button> build(LandingContext ctx);
}


