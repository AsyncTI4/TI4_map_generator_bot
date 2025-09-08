package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.SourceEmojis;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class LightWaveDeflectorButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.hasTech("baldrick_lwd");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(
                Buttons.gray("exhaustTech_baldrick_lwd", "Exhaust Light/Wave Deflector", SourceEmojis.IgnisAurora));
    }
}
