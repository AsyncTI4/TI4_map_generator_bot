package ti4.discord.interactions.buttons.handlers.agenda.resolver;

import java.util.Objects;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.service.unit.DestroyUnitService;

public class DisarmamentAgendaResolver implements AgendaResolver {
    @Override
    public String agendaId() {
        return "disarmament";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        for (Player player : game.getRealPlayers()) {
            if (player.getPlanets().contains(winner.toLowerCase())) {
                StringBuilder units = new StringBuilder();
                Tile tile = game.getTileFromPlanet(winner);
                Planet uH = ButtonHelper.getUnitHolderFromPlanetName(winner, game);
                int count = 0;
                if (uH != null) { // kill coexisters first
                    for (Player p2 : game.getRealPlayersExcludingThis(player)) {
                        for (UnitKey uk : uH.getUnitsByStateForPlayer(p2).keySet()) {
                            if (p2.getUnitFromUnitKey(uk).getIsGroundForce()) {
                                int amt = uH.getUnitCount(uk);
                                count += amt;
                                DestroyUnitService.destroyUnit(event, tile, game, uk, amt, uH, false);
                                units.repeat(
                                        Objects.requireNonNull(uk.unitEmoji().emojiString()), amt);
                            }
                        }
                    }
                    for (UnitKey uk : uH.getUnitsByStateForPlayer(player).keySet()) {
                        if (player.getUnitFromUnitKey(uk).getIsGroundForce()) {
                            int amt = uH.getUnitCount(uk);
                            count += amt;
                            DestroyUnitService.destroyUnit(event, tile, game, uk, amt, uH, false);
                            units.repeat(Objects.requireNonNull(uk.unitEmoji().emojiString()), amt);
                        }
                    }
                    if (count > 0) {
                        player.setTg(player.getTg() + count);
                        ButtonHelperAgents.resolveArtunoCheck(player, count);
                        ButtonHelperAbilities.pillageCheck(player, game);
                    }
                }
                if (game.isFowMode()) {
                    MessageHelper.sendMessageToChannel(
                            player.getPrivateChannel(),
                            "Destroyed all ground forces (" + units + ") on " + winner + ", and gave "
                                    + player.getRepresentation() + " " + count + " trade good" + (count == 1 ? "" : "s")
                                    + ".");
                    MessageHelper.sendMessageToChannel(
                            game.getMainGameChannel(),
                            "Destroyed all ground forces on the elected planet. The owner of said units has been justly compensated.");
                } else {
                    MessageHelper.sendMessageToChannel(
                            game.getMainGameChannel(),
                            "Destroyed all ground forces (" + units + ") on " + winner + ", and gave "
                                    + player.getRepresentation() + " " + count + " trade good" + (count == 1 ? "" : "s")
                                    + " in compensation.");
                }
            }
        }
    }
}
