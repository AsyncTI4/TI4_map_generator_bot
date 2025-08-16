package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class RealityFieldImpactorButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.hasTech("dspharb");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(Buttons.gray(
                "declareUse_Reality Field Impactor", "Declare Reality Field Impactor", FactionEmojis.pharadn));
    }
}


