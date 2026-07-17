package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.helpers.FoWHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;

public final class CombatDronesButton implements PostMovementAbilityButton {
    public boolean enabled(PostMovementButtonContext ctx) {
        return (ctx.player().hasAbility("combat_drones") || ctx.player().hasTech("tf-dronehosts"))
                && FoWHelper.playerHasFightersInSystem(ctx.player(), ctx.tile());
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.blue(
                ctx.player().factionButtonChecker() + "combatDrones",
                "Use Combat Drones Ability",
                FactionEmojis.mirveda));
    }
}
