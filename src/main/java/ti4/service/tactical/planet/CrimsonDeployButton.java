package ti4.service.tactical.planet;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.LandingContext;
import ti4.service.tactical.PlanetAbilityButton;

public final class CrimsonDeployButton implements PlanetAbilityButton {
    public boolean enabled(LandingContext ctx) {
        return ctx.mainPlayer.hasUnit("crimson_mech")
                && (ctx.tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_BREACH_ACTIVE)
                        || FoWHelper.otherPlayersHaveShipsInSystem(ctx.mainPlayer, ctx.tile, ctx.game))
                && ButtonHelperFactionSpecific.vortexButtonAvailable(
                        ctx.game, Units.getUnitKey(UnitType.Mech, ctx.mainPlayer.getColor()));
    }

    public List<Button> build(LandingContext ctx) {
        String id = "revenantDeploy_" + ctx.planetName;
        String label = "Deploy Mech On " + ctx.planetRep;
        return List.of(Buttons.green(id, label, FactionEmojis.Crimson));
    }
}
