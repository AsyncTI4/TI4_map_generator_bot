package ti4.helpers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;
import ti4.service.fow.GMService;

@UtilityClass
public class ObjectiveHelper {

    public void secondHalfOfPeakStage1(Game game, Player player, int loc) {
        String obj = game.peekAtStage1(loc, player);
        PublicObjectiveModel po = Mapper.getPublicObjective(obj);
        MessageEmbed embed = po.getRepresentationEmbed();
        String msg = player.getRepresentationUnfogged() + ", stage 1 public objective at location " + loc + ":";
        MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), msg, embed);
        report(game, player, "Stage 1", loc);
    }

    public void secondHalfOfPeakStage2(Game game, Player player, int loc) {
        String obj = game.peekAtStage2(loc, player);
        PublicObjectiveModel po = Mapper.getPublicObjective(obj);
        MessageEmbed embed = po.getRepresentationEmbed();
        String msg = player.getRepresentationUnfogged() + ", stage 2 public objective at location " + loc + ":";
        MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), msg, embed);
        report(game, player, "Stage 2", loc);
    }

    private void report(Game game, Player peeker, String type, int loc) {
        String msg = peeker.getRepresentationUnfoggedNoPing() + " peeked at the ";
        msg += StringHelper.ordinal(loc) + " " + type + " objective.";
        if (game.isFowMode()) {
            GMService.logActivity(game, msg, false);
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
        }
    }
}
