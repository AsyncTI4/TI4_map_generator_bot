package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class MidasTurbineButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.hasTech("dsvadeb")
                && !ctx.player.getExhaustedTechs().contains("dsvadeb")
                && !ctx.player.getExhaustedTechs().contains("tf-dsvadeb");
    }

    public List<Button> build(MoveContext ctx) {

        if (ctx.game.isTwilightsFallMode()) {
            return List.of(Buttons.green(
                    ctx.player.factionButtonChecker() + "exhaustTech_tf-dsvadeb",
                    "Exhaust Midas Turbine",
                    FactionEmojis.vaden));
        }
        return List.of(Buttons.green(
                ctx.player.factionButtonChecker() + "exhaustTech_dsvadeb",
                "Exhaust Midas Turbine",
                FactionEmojis.vaden));
    }
}
