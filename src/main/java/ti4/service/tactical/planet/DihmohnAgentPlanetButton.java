package ti4.service.tactical.planet;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Units.UnitType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.LandingContext;
import ti4.service.tactical.PlanetAbilityButton;

public final class DihmohnAgentPlanetButton implements PlanetAbilityButton {
    public boolean enabled(LandingContext ctx) {
        return (ctx.planet.getUnitCount(UnitType.Infantry, ctx.mainPlayer) > 0
                        || ctx.planet.getUnitCount(UnitType.Mech, ctx.mainPlayer) > 0)
                && ctx.mainPlayer.hasUnexhaustedLeader("dihmohnagent");
    }

    public List<Button> build(LandingContext ctx) {
        String id = "exhaustAgent_dihmohnagent_" + ctx.planetName;
        String label = "Use Dih-Mohn Agent on " + ctx.planetRep;
        return List.of(Buttons.green(id, label, FactionEmojis.dihmohn));
    }
}


