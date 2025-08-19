package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class RaghsCallButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player.getPromissoryNotes().containsKey("ragh");
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return ButtonHelperFactionSpecific.getRaghsCallButtons(ctx.player, ctx.game, ctx.tile);
    }
}
