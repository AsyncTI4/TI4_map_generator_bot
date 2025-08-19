package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class MidasTurbineButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.getTechs().contains("dsvadeb")
                && !ctx.player.getExhaustedTechs().contains("dsvadeb");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(Buttons.green(
                ctx.player.finChecker() + "exhaustTech_dsvadeb", "Exhaust Midas Turbine", FactionEmojis.vaden));
    }
}
