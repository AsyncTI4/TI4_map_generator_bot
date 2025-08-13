package ti4.buttons.handlers.agenda.resolver;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;

public class RedistributionAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "redistribution";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        for (Player player : game.getRealPlayers()) {
            if (player.getPlanets().contains(winner.toLowerCase())) {
                Planet uH = ButtonHelper.getUnitHolderFromPlanetName(winner, game);
                Tile tile = game.getTileFromPlanet(winner);
                if (tile != null) {
                    DestroyUnitService.destroyAllUnits(event, tile, game, uH, false);
                }

                boolean containsDMZ = uH.getTokenList().stream().anyMatch(token -> token.contains("dmz"));
                if (containsDMZ) {
                    String dmzString =
                            "Because " + Helper.getPlanetRepresentation(winner, game) + " is the _Demilitarized Zone_,";
                    dmzString += " there is no point in choosing a player to place an infantry.";
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), dmzString);
                    continue;
                }
                if (AgendaHelper.getPlayersWithLeastPoints(game).size() == 1) {
                    Player p2 = AgendaHelper.getPlayersWithLeastPoints(game).getFirst();
                    if (tile != null) {
                        AddUnitService.addUnits(event, tile, game, p2.getColor(), "1 inf " + winner);
                    }
                    String resolveStr = "1 " + p2.getColor() + " infantry was added to "
                            + Helper.getPlanetRepresentation(winner, game) + " automatically.";
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), resolveStr);
                    continue;
                }
                List<Button> buttons = new ArrayList<>();
                for (Player player2 : AgendaHelper.getPlayersWithLeastPoints(game)) {
                    if (game.isFowMode()) {
                        buttons.add(Buttons.green(
                                "colonialRedTarget_" + player2.getFaction() + "_" + winner, player2.getColor()));
                    } else {
                        buttons.add(Buttons.green(
                                "colonialRedTarget_" + player2.getFaction() + "_" + winner, player2.getFaction()));
                    }
                }

                String msg =
                        player.getRepresentationUnfogged() + ", please choose who you wish to place an infantry on";
                msg += ", and thus gain control of " + Helper.getPlanetRepresentation(winner, game) + ".";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                if (game.isFowMode()) {
                    MessageHelper.sendMessageToChannel(
                            game.getMainGameChannel(),
                            "Removed all units and gave player who owns the planet the option of who to give it to.");
                }
            }
        }
    }
}
