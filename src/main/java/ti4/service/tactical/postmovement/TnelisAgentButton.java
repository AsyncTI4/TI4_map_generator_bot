package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.FoWHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class TnelisAgentButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player.hasUnexhaustedLeader("tnelisagent")
                && FoWHelper.playerHasShipsInSystem(ctx.player, ctx.tile)
                && FoWHelper.otherPlayersHaveUnitsInSystem(ctx.player, ctx.tile, ctx.game);
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.gray(
                "exhaustAgent_tnelisagent_" + ctx.player.getFaction(), "Use Tnelis Agent", FactionEmojis.tnelis));
    }
}


