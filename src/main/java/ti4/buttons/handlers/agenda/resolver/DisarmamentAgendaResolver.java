package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.unit.DestroyUnitService;

public class DisarmamentAgendaResolver implements AgendaResolver {
    @Override
    public String getAgID() {
        return "disarmamament";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int aID, String winner) {
        for (Player player : game.getRealPlayers()) {
            if (player.getPlanets().contains(winner.toLowerCase())) {
                Tile tile = game.getTileFromPlanet(winner);
                Planet uH = ButtonHelper.getUnitHolderFromPlanetName(winner, game);
                int count = 0;
                for (UnitKey uk : uH.getUnitsByStateForPlayer(player).keySet()) {
                    if (player.getUnitFromUnitKey(uk).getIsGroundForce()) {
                        int amt = uH.getUnitCount(uk);
                        count += amt;
                        DestroyUnitService.destroyUnit(event, tile, game, uk, amt, uH, false);
                    }
                }
                if (count > 0) {
                    player.setTg(player.getTg() + count);
                    ButtonHelperAgents.resolveArtunoCheck(player, count);
                    ButtonHelperAbilities.pillageCheck(player, game);
                }
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(),
                        "Removed all ground forces and gave the player the appropriate amount of trade goods.");
            }
        }
    }
}
