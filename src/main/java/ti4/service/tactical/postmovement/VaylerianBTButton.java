package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.FoWHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class VaylerianBTButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player.hasUnlockedBreakthrough("vaylerianbt")
                && FoWHelper.playerHasActualShipsInSystem(ctx.player, ctx.tile)
                && ctx.player.getAcCount() > 0;
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.blue(
                ctx.player.finChecker() + "useVaylerianBT_" + ctx.tile.getPosition(),
                "Discard AC To Move Ship",
                FactionEmojis.vaylerian));
    }
}
