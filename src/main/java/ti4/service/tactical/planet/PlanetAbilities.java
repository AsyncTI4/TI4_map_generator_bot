package ti4.service.tactical.planet;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.emoji.FactionEmojis;

public final class PlanetAbilities {
    private PlanetAbilities() {}

    public interface PlanetButtonAbility {
        boolean enabled(LandingContext ctx);

        void contribute(LandingContext ctx, List<Button> buttons);
    }

    public static final List<PlanetButtonAbility> ABILITIES = List.of(new DihmohnAgent(), new TnelisDeploy());

    public static final class DihmohnAgent implements PlanetButtonAbility {
        public boolean enabled(LandingContext ctx) {
            return (ctx.planet.getUnitCount(UnitType.Infantry, ctx.mainPlayer) > 0
                            || ctx.planet.getUnitCount(UnitType.Mech, ctx.mainPlayer) > 0)
                    && ctx.mainPlayer.hasUnexhaustedLeader("dihmohnagent");
        }

        public void contribute(LandingContext ctx, List<Button> buttons) {
            String id = "exhaustAgent_dihmohnagent_" + ctx.planetName;
            String label = "Use Dih-Mohn Agent on " + ctx.planetRep;
            buttons.add(Buttons.green(id, label, FactionEmojis.dihmohn));
        }
    }

    public static final class TnelisDeploy implements PlanetButtonAbility {
        public boolean enabled(LandingContext ctx) {
            return ctx.mainPlayer.hasUnit("tnelis_mech")
                    && ctx.tile.getSpaceUnitHolder().getUnitCount(UnitType.Destroyer, ctx.mainPlayer) > 0
                    && ButtonHelperFactionSpecific.vortexButtonAvailable(
                            ctx.game, Units.getUnitKey(UnitType.Mech, ctx.mainPlayer.getColor()));
        }

        public void contribute(LandingContext ctx, List<Button> buttons) {
            String id = "tnelisDeploy_" + ctx.planetName;
            String label = "Deploy Mech on " + ctx.planetRep;
            buttons.add(Buttons.green(id, label, FactionEmojis.tnelis));
        }
    }
}
