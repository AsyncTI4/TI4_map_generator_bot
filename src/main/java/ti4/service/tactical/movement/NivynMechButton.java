package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Units.UnitType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;
import ti4.service.unit.CheckUnitContainmentService;

public final class NivynMechButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return (ctx.player.ownsUnit("nivyn_mech")
            && CheckUnitContainmentService.getTilesContainingPlayersUnits(
                ctx.game, ctx.player, UnitType.Mech)
                .contains(ctx.active))
            || ctx.player.ownsUnit("nivyn_mech2");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(Buttons.gray("nivynMechStep1_", "Use Nivyn Mech", FactionEmojis.nivyn));
    }
}
