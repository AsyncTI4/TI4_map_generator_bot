package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.player.SendTG;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DrawRelic extends GenericRelicAction {

    public DrawRelic() {
        super(Constants.RELIC_DRAW, "Draw a relic");
    }

    @Override
    public void doAction(Player player, SlashCommandInteractionEvent event) {
        String relicID = getActiveMap().drawRelic();
        if (relicID.isEmpty()) {
            MessageHelper.replyToMessage(event, "Relic deck is empty");
            return;
        }
        player.addRelic(relicID);
        String[] relicData = Mapper.getRelic(relicID).split(";");
        String relicString =  Helper.getPlayerRepresentation(event, player) + " drew a Relic:\n" + Emojis.Relic + relicData[0] + " - " + relicData[1];
        MessageHelper.replyToMessage(event, relicString);
    }
}
