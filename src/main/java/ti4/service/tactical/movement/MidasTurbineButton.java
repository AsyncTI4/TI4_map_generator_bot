package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class MidasTurbineButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.hasExactTech("dsvadeb") && !ctx.player.isTechExhausted("dsvadeb");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(Buttons.green(
                ctx.player.factionButtonChecker() + "exhaustTech_dsvadeb",
                "Exhaust Midas Turbine",
                FactionEmojis.vaden));
    }
}
