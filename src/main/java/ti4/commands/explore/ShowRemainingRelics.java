package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.List;

public class ShowRemainingRelics extends GenericRelicAction {

    public ShowRemainingRelics() {
        super(Constants.RELIC_SHOW_REMAINING, "Show remaining relics in deck", true);
    }

    @Override
    public void doAction(Player player, SlashCommandInteractionEvent event) {
        List<String> allRelics = new ArrayList<>(getActiveMap().getAllRelics());
        allRelics.remove(Constants.ENIGMATIC_DEVICE);
        StringBuilder text;
        if (allRelics.isEmpty()) {
            text = new StringBuilder("RELIC DECK IS EMPTY");
        } else {
            text = new StringBuilder("RELICS IN DECK\n");
            for (String relicId : allRelics) {
                String[] relicData = Mapper.getRelic(relicId).split(";");
                text.append(relicData[0]).append("\n");
            }
        }
        MessageHelper.replyToMessage(event, text.toString());
    }
}
