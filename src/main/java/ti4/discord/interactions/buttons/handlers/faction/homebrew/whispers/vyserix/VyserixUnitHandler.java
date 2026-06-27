package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.vyserix;

import lombok.experimental.UtilityClass;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.service.combat.CombatRollType;

@UtilityClass
public class VyserixUnitHandler {

    public static int getTechnotemplarModifier(Player player, UnitHolder unitHolder, CombatRollType rollType) {
        if (!(unitHolder instanceof Planet)) return 0;
        if (rollType != CombatRollType.SpaceCannonDefence
                && rollType != CombatRollType.SpaceCannonOffence
                && rollType != CombatRollType.AFB) return 0;
        return 2 * unitHolder.getUnitCount(UnitType.Mech, player.getColor());
    }
}
