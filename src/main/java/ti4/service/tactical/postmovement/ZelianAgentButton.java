package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.helpers.Units.UnitType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.modules.ZelianAgent;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class ZelianAgentButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player().hasUnexhaustedLeader("zelianagent")
                && ctx.tile()
                                .getUnitHolders()
                                .get("space")
                                .getUnitCount(UnitType.Infantry, ctx.player().getColor())
                        > 0;
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(
                Buttons.gray(ZelianAgent.buttonId(ctx.player()), "Use Zelian Agent Yourself", FactionEmojis.zelian));
    }
}
