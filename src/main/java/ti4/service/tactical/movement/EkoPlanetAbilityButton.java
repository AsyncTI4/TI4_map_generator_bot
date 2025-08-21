package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class EkoPlanetAbilityButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.getPlanets().contains("eko")
                && !ctx.player.getExhaustedPlanetsAbilities().contains("eko");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(Buttons.gray(
                ctx.player.finChecker() + "planetAbilityExhaust_" + "eko", "Use Eko's Ability To Ignore Anomalies"));
    }
}
