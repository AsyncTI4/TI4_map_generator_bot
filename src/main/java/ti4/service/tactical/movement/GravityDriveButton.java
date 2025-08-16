package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.interactions/components/buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.SourceEmojis;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class GravityDriveButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.hasTech("baldrick_gd");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(Buttons.gray("exhaustTech_baldrick_gd", "Exhaust Gravity Drive", SourceEmojis.IgnisAurora));
    }
}


