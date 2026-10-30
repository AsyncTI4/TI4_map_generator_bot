package ti4.service.tactical.planet;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.valhavn.ValhavnUnitHandler;
import ti4.service.tactical.LandingContext;
import ti4.service.tactical.PlanetAbilityButton;

public final class ValhavnPdsCommitButton implements PlanetAbilityButton {
    @Override
    public boolean enabled(LandingContext ctx) {
        return ValhavnUnitHandler.canCommitThroughBilrost(ctx);
    }

    @Override
    public List<Button> build(LandingContext ctx) {
        return ValhavnUnitHandler.getBilrostCommitButtons(ctx);
    }
}
