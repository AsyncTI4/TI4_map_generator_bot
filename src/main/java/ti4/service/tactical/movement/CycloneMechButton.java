package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.zephyrion.ZephyrionUnitHandler;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class CycloneMechButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ZephyrionUnitHandler.hasUndamagedCyclone(ctx.game, ctx.player);
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(ZephyrionUnitHandler.cycloneButton(ctx.player));
    }
}
