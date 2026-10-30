package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.service.leader.agent.modules.ExhaustOnlyAgents;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class FirmamentAgentButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.hasUnexhaustedLeader("firmamentagent");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(ExhaustOnlyAgents.FIRMAMENT.button(ctx.player));
    }
}
