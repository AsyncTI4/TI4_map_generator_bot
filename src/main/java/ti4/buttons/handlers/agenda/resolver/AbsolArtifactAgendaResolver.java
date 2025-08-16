package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.buttons.Buttons;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Helper;
import ti4.image.TileGenerator;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.emoji.PlanetEmojis;

public class AbsolArtifactAgendaResolver implements ForAgainstAgendaResolver {
    @Override
    public String getAgendaId() {
        return "absol_artifact";
    }

    @Override
    public void handleFor(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        TextChannel watchParty = AgendaHelper.watchPartyChannel(game);
        String watchPartyPing = AgendaHelper.watchPartyPing(game);
        if (watchParty != null && !game.isFowMode()) {
            Tile tile = game.getMecatolTile();
            if (tile != null) {
                FileUpload systemWithContext =
                        new TileGenerator(game, event, null, 1, tile.getPosition()).createFileUpload();
                String message = "# Ixthian Artifact has resolved! " + watchPartyPing + "\n"
                        + AgendaHelper.getSummaryOfVotes(game, true);
                MessageHelper.sendMessageToChannel(watchParty, message);
                MessageHelper.sendMessageWithFile(
                        watchParty, systemWithContext, "Surrounding Mecatol Rex In " + game.getName(), false);
            }
        }
        var ixthianButton = Buttons.green("rollIxthian", "Roll Ixthian Artifact", PlanetEmojis.Mecatol);
        String msg = game.getPing() + "Click this button to roll for _Ixthian Artifact_! 🥁";
        MessageHelper.sendMessageToChannelWithButton(game.getMainGameChannel(), msg, ixthianButton);
    }

    @Override
    public void handleAgainst(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Against on _Ixthian Artifact_‽ Disgraceful.");
        Integer poIndex = game.addCustomPO("Ixthian Rex Point", 1);
        StringBuilder message = new StringBuilder();
        message.append("Custom objective _Ixthian Rex Point_ has been added.\n");
        for (Player playerWL : game.getRealPlayers()) {
            if (playerWL.getPlanets().contains("mr")) {
                game.scorePublicObjective(playerWL.getUserID(), poIndex);
                message.append(playerWL.getRepresentation()).append(" scored _Ixthian Rex Point_.\n");
                Helper.checkEndGame(game, playerWL);
            }
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message.toString());
    }
}
