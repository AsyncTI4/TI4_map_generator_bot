package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class FirmamentAgentButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.hasUnexhaustedLeader("firmamentagent");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(Buttons.gray("exhaustAgent_firmamentagent", "Use Firmament Agent", FactionEmojis.Firmament));
    }
}
