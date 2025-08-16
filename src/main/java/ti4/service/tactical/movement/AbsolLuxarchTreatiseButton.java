package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.tactical.MoveAbilityButton;
import ti4.service.tactical.MoveContext;

public final class AbsolLuxarchTreatiseButton implements MoveAbilityButton {
    public boolean enabled(MoveContext ctx) {
        return ctx.player.hasRelicReady("absol_luxarchtreatise");
    }

    public List<Button> build(MoveContext ctx) {
        return List.of(Buttons.gray(
                "exhaustRelic_absol_luxarchtreatise", "Exhaust Luxarch Treatise", ExploreEmojis.Relic));
    }
}


