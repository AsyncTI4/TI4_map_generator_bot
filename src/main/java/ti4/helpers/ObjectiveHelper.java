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
        String obj = game.peekAtStage1(loc1, player);
        PublicObjectiveModel po = Mapper.getPublicObjective(obj);
        String sb = player.getRepresentationUnfogged() + " **Stage 1 Public Objective at location "
                + loc1 + "**" + "\n" + po.getRepresentation()
                + "\n";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb);
    }

    public void secondHalfOfPeakStage2(Game game, Player player, int loc1) {
        String obj = game.peekAtStage2(loc1, player);
        PublicObjectiveModel po = Mapper.getPublicObjective(obj);
        String sb = player.getRepresentationUnfogged() + " **Stage 2 Public Objective at location "
                + loc1 + "**" + "\n" + po.getRepresentation()
                + "\n";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb);
    }
}
