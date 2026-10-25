package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.planet.IxthLegendaryButtonHandler;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class HoracePlanetAbilityButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.getPlanets().contains("horace")
                && !ctx.player.getExhaustedPlanetsAbilities().contains("horace")
                && IxthLegendaryButtonHandler.hasHoraceTarget(ctx.game, ctx.player);
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(Buttons.gray(
                ctx.player.factionButtonChecker() + "planetAbilityExhaust_" + "horace",
                "Use Horace's Ability To Ignore Anomalies"));
    }
}
