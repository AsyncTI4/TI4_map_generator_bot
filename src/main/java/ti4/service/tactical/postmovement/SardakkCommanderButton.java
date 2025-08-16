package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.ButtonHelperCommanders;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class SardakkCommanderButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.game.playerHasLeaderUnlockedOrAlliance(ctx.player, "sardakkcommander");
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return ButtonHelperCommanders.getSardakkCommanderButtons(ctx.game, ctx.player, null);
    }
}


