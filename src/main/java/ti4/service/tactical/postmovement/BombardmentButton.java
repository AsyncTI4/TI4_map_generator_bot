package ti4.service.tactical.postmovement;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class BombardmentButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.tile().getUnitHolders().size() > 1
                && ti4.helpers.ButtonHelper.getTilesOfUnitsWithBombard(ctx.player(), ctx.game())
                        .contains(ctx.tile());
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        List<Button> buttons = new ArrayList<>();
        if (ctx.player().hasTechReady("aobrynfolkrg")) {
            buttons.add(Buttons.red(
                    ctx.player().factionButtonChecker() + "exhaustTech_aobrynfolkrg",
                    "Use Roar of the Gjallarhorn",
                    FactionEmojis.brynfolk));
        }
        if (ctx.tile().getUnitHolders().size() > 2) {
            buttons.add(Buttons.gray(
                    "bombardConfirm_combatRoll_" + ctx.tile().getPosition() + "_space_" + CombatRollType.bombardment,
                    "Roll BOMBARDMENT"));
        } else {
            buttons.add(Buttons.gray(
                    "combatRoll_" + ctx.tile().getPosition() + "_space_" + CombatRollType.bombardment,
                    "Roll BOMBARDMENT"));
        }
        return buttons;
    }
}
