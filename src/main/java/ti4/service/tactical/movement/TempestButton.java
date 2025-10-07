package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.MiscEmojis;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class TempestButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.hasPlanet("tempesta")
                && !ctx.player.getExhaustedPlanetsAbilities().contains("tempesta");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(
                Buttons.gray("planetAbilityExhaust_tempesta", "Exhaust Tempesta Ability", MiscEmojis.LegendaryPlanet));
    }
}
