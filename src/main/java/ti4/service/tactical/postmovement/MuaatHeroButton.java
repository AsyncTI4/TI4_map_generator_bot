package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Units.UnitType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;
import ti4.service.unit.CheckUnitContainmentService;

public final class MuaatHeroButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player.hasLeaderUnlocked("muaathero")
                && !ctx.tile.isMecatol()
                && !ctx.tile.isHomeSystem(ctx.game)
                && CheckUnitContainmentService.getTilesContainingPlayersUnits(ctx.game, ctx.player, UnitType.Warsun)
                        .contains(ctx.tile);
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.blue(
                ctx.player.finChecker() + "novaSeed_" + ctx.tile.getPosition(),
                "Nova Seed This Tile",
                FactionEmojis.Muaat));
    }
}
