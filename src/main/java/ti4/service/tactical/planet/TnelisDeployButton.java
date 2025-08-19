package ti4.service.tactical.planet;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.LandingContext;
import ti4.service.tactical.PlanetAbilityButton;

public final class TnelisDeployButton implements PlanetAbilityButton {
    public boolean enabled(LandingContext ctx) {
        return ctx.mainPlayer.hasUnit("tnelis_mech")
                && ctx.tile.getSpaceUnitHolder().getUnitCount(UnitType.Destroyer, ctx.mainPlayer) > 0
                && ButtonHelperFactionSpecific.vortexButtonAvailable(
                        ctx.game, Units.getUnitKey(UnitType.Mech, ctx.mainPlayer.getColor()));
    }

    public List<Button> build(LandingContext ctx) {
        String id = "tnelisDeploy_" + ctx.planetName;
        String label = "Deploy Mech on " + ctx.planetRep;
        return List.of(Buttons.green(id, label, FactionEmojis.tnelis));
    }
}
