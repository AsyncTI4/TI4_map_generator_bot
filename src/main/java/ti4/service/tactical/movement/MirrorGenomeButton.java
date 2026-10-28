package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.service.leader.genome.homebrew.tknova.MirrorGenome;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class MirrorGenomeButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.hasUnexhaustedLeader("tknovamirroragent");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(MirrorGenome.button(ctx.player));
    }
}
