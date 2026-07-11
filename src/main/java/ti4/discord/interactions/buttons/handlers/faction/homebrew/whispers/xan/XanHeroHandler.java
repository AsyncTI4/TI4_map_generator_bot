package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.xan;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.StringHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.message.MessageHelper;

@UtilityClass
public class XanHeroHandler {

    public static void postInitialButtons(Game game, Player player) {
        int amount = 0;
        for (Tile tile : game.getTiles()) {
            for (UnitHolder uh : tile.getUnitHolderValues()) {
                for (UnitKey uk : uh.getUnitKeys()) {
                    amount += uh.getUnitCountForState(uk, UnitState.dmg);
                }
            }
        }
        game.getTiles().stream()
                .flatMap(t -> t.getUnitHolderValues().stream())
                .forEach(uh -> uh.removeAllUnitDamage(player.getColorID()));
        String gainedTg = player.gainTG(amount, true);
        String message = player.toString() + " repaired all " + amount
                + " of their damaged units, and consequently gained " + StringHelper.pluralize(amount, "trade good")
                + " " + gainedTg + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelperAgents.resolveArtunoCheck(player, amount);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.toString()
                        + " can now repair other players' units near their space docks (not automated, use `/remove_all_sustain_damage`).");
    }
}
