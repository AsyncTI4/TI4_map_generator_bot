package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class ZelianHeroButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player.hasLeaderUnlocked("zelianhero")
                && !ctx.tile.isMecatol()
                && ButtonHelper.getTilesOfUnitsWithBombard(ctx.player, ctx.game).contains(ctx.tile);
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.blue(
                ctx.player.finChecker() + "celestialImpact_" + ctx.tile.getPosition(),
                "Celestial Impact This Tile",
                FactionEmojis.zelian));
    }
}
