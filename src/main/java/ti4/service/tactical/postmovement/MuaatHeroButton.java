package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.helpers.Units.UnitType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;
import ti4.service.unit.UnitQueryService;

public final class MuaatHeroButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player().hasLeaderUnlocked("muaathero")
                && ((!ctx.tile().isMecatol(ctx.game()) && !ctx.tile().isHomeSystem(ctx.game()))
                        || ctx.game().isWildWildGalaxyMode())
                && !ctx.tile().getPosition().contains("frac")
                && UnitQueryService.getTilesContainingPlayersUnits(ctx.game(), ctx.player(), UnitType.Warsun)
                        .contains(ctx.tile());
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.blue(
                ctx.player().factionButtonChecker() + "novaSeed_" + ctx.tile().getPosition(),
                "Nova Seed This Tile",
                FactionEmojis.Muaat));
    }
}
