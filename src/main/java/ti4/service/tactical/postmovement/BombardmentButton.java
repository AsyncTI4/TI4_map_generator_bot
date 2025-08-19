package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.combat.CombatRollType;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class BombardmentButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.tile.getUnitHolders().size() > 1
                && ti4.helpers.ButtonHelper.getTilesOfUnitsWithBombard(ctx.player, ctx.game)
                        .contains(ctx.tile);
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        if (ctx.tile.getUnitHolders().size() > 2) {
            return List.of(Buttons.gray(
                    "bombardConfirm_combatRoll_" + ctx.tile.getPosition() + "_space_" + CombatRollType.bombardment,
                    "Roll BOMBARDMENT"));
        } else {
            return List.of(Buttons.gray(
                    "combatRoll_" + ctx.tile.getPosition() + "_space_" + CombatRollType.bombardment,
                    "Roll BOMBARDMENT"));
        }
    }
}
