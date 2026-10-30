package ti4.discord.interactions.buttons.handlers.combat;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AutoAssignGroundHitsButtonIds;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class SpaceCannonDefenceHitButtons {

    public static List<Button> beforeACancel(Player target, Tile tile, String planet, int hits) {
        List<Button> buttons = assignment(target, tile, planet, hits, "deleteThis_groundcombat");
        buttons.add(Buttons.gray(
                target.factionButtonChecker()
                        + CancelSpaceCannonDefenceHitsButtonId.of(tile.getPosition(), hits, planet),
                "Cancel a Hit"));
        return buttons;
    }

    public static List<Button> afterACancel(Player target, Tile tile, String planet, int hits) {
        return assignment(target, tile, planet, hits, "_groundcombat");
    }

    private static List<Button> assignment(Player target, Tile tile, String planet, int hits, String manualSuffix) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                target.factionButtonChecker() + AutoAssignGroundHitsButtonIds.format(planet, hits),
                "Auto-assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + manualSuffix,
                "Manually Assign Hit" + (hits == 1 ? "" : "s")));
        return buttons;
    }
}
