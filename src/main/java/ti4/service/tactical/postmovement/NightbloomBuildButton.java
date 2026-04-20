package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.helpers.Units.UnitType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;
import ti4.service.unit.CheckUnitContainmentService;

public final class NightbloomBuildButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player.ownsUnit("greentf_flagship")
                && CheckUnitContainmentService.getTilesContainingPlayersUnits(ctx.game, ctx.player, UnitType.Flagship)
                        .contains(ctx.tile);
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.blue(
                ctx.player.finChecker() + "nightbloomBuild", "Do Nightbloom (Flagship) Build", FactionEmojis.greentf));
    }
}
