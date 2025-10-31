package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.MiscEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class WeirdWormholeButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.game.isWeirdWormholesMode();
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.green(
                ctx.player.finChecker() + "getWeirdWormholeButtons_" + ctx.tile.getPosition(),
                "Units Travelled Through Weird Wormhole",
                MiscEmojis.WHalpha));
    }
}
