package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.helpers.FoWHelper;
import ti4.service.leader.agent.modules.NokarAgent;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class NokarAgentButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player().hasUnexhaustedLeader("nokaragent")
                && FoWHelper.playerHasShipsInSystem(ctx.player(), ctx.tile());
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(NokarAgent.offer(ctx.player()));
    }
}
