package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.service.leader.agent.modules.homebrew.bluereverie.QhetAgent;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class QhetAgentButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.hasUnexhaustedLeader("qhetagent") && ctx.active != null;
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(QhetAgent.button(ctx.player));
    }
}
