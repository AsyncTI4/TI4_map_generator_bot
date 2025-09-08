package ti4.helpers;

import lombok.experimental.UtilityClass;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

@UtilityClass
public class ObjectiveHelper {

    public void secondHalfOfPeakStage1(Game game, Player player, int loc1) {
        secondHalfOfPeakStage1(game, player, loc1, false);
    }

    public void secondHalfOfPeakStage1(Game game, Player player, int loc1, boolean fullEmbed) {
        String obj = game.peekAtStage1(loc1, player);
        PublicObjectiveModel po = Mapper.getPublicObjective(obj);
        if (fullEmbed) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "Stage 1 public objective at location " + loc1 + ".");
            player.getCardsInfoThread()
                    .sendMessageEmbeds(po.getRepresentationEmbed())
                    .queue();
        } else {
            String sb = player.getRepresentationUnfogged() + ", stage 1 public objective at location "
                    + loc1 + ":\n" + po.getRepresentation(!po.getAlias().equalsIgnoreCase(Constants.IMPERIUM_REX_ID))
                    + "\n";
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb);
        }
    }

    public void secondHalfOfPeakStage2(Game game, Player player, int loc1) {
        String obj = game.peekAtStage2(loc1, player);
        PublicObjectiveModel po = Mapper.getPublicObjective(obj);
        String sb = player.getRepresentationUnfogged() + ", stage 2 public objective at location "
                + loc1 + ":\n" + po.getRepresentation()
                + "\n";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb);
    }
}
