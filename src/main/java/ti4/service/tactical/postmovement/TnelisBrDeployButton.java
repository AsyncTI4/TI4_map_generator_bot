package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.TnelisUnitHandler;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class TnelisBrDeployButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return TnelisUnitHandler.canDeploy(ctx.game(), ctx.player(), ctx.tile());
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.green(
                ctx.player().factionButtonChecker() + "tnelisBrDeploy_"
                        + ctx.tile().getPosition(),
                "Deploy Daedalon",
                FactionEmojis.tnelis));
    }
}
