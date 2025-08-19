package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class EyeOfVogulButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.hasRelic("eye_of_vogul");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(Buttons.gray("eyeOfVogul", "Purge Eye of Vogul", ExploreEmojis.Relic));
    }
}
