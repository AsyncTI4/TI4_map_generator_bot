package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.MiscEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class RiftUsedButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return !ctx.game.getStoredValue("possiblyUsedRift").isEmpty();
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.green(
                ctx.player.finChecker() + "getRiftButtons_" + ctx.tile.getPosition(),
                "Units Travelled Through Gravity Rift",
                MiscEmojis.GravityRift));
    }
}


