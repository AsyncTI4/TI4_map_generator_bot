package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.helpers.FoWHelper;
import ti4.service.leader.agent.modules.homebrew.TnelisAgent;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class TnelisAgentButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player().hasUnexhaustedLeader("tnelisagent")
                && FoWHelper.playerHasShipsInSystem(ctx.player(), ctx.tile())
                && FoWHelper.otherPlayersHaveUnitsInSystem(ctx.player(), ctx.tile(), ctx.game());
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(TnelisAgent.button(ctx.player()));
    }
}
