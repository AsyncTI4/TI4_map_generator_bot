package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class VaylerianCommanderButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.game.playerHasLeaderUnlockedOrAlliance(ctx.player, "vayleriancommander");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(
                Buttons.gray("declareUse_Vaylerian Commander", "Use Vaylerian Commander", FactionEmojis.vaylerian));
    }
}
