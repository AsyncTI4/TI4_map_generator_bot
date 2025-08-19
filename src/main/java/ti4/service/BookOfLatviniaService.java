package ti4.service;

import java.util.HashSet;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.emoji.MiscEmojis;

public class BookOfLatviniaService {

    private static final String id = "bookoflatvinia";

    private static RelicModel relic() {
        return Mapper.getRelic(id);
    }

    public static void purgeBookOfLatvinia(ButtonInteractionEvent event, Game game, Player player) {
        Set<String> skips = new HashSet<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            Planet p = game.getUnitHolderFromPlanet(planet);
            if (p != null) {
                skips.addAll(p.getTechSpecialities());
            }
        }
        for (TechnologyType type : TechnologyType.mainFour) {
            if (!skips.contains(type.toString())) {
                stealSpeakerToken(event, game, player);
                return;
            }
        }
        scoreBookOfLatviniaPoint(event, game, player);
    }

    private static void stealSpeakerToken(ButtonInteractionEvent event, Game game, Player player) {
        Player prevSpeaker = game.getSpeaker();
        game.setSpeaker(player);

        String msg = prevSpeaker.getRepresentation()
                + ", the speaker token has been ripped from your grasp by the _Book of Latvinia_. "
                + MiscEmojis.SpeakerToken;
        MessageHelper.sendMessageToChannel(prevSpeaker.getCorrectChannel(), msg);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " you have gained the speaker token. " + MiscEmojis.SpeakerToken);
        }
    }

    private static void scoreBookOfLatviniaPoint(ButtonInteractionEvent event, Game game, Player player) {
        String book = relic().getName();
        Integer id = game.getRevealedPublicObjectives().getOrDefault(book, null);

        String message;
        if (id != null) {
            game.scorePublicObjective(player.getUserID(), id);
            message = player.getRepresentation() + " has scored the \"Book of Latvinia\" custom objective.";
        } else {
            id = game.addCustomPO(book, 1);
            game.scorePublicObjective(player.getUserID(), id);
            message = "Custom objective \"Book of Latvinia\" has been added.\n" + player.getRepresentation()
                    + " has scored the \"Book of Latvinia\" custom objective.";
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        Helper.checkEndGame(game, player);
    }
}
