package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.text.NumberFormat;
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

        Integer deckCount = allRelics.size();
        Double deckDrawChance = deckCount == 0 ? 0.0 : 1.0 / deckCount;
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        formatPercent.setMaximumFractionDigits(1);
        
        StringBuilder text;
        if (allRelics.isEmpty()) {
            text = new StringBuilder("**RELIC DECK IS EMPTY**");
        } else {
            text = new StringBuilder(Emojis.Relic).append(" **RELICS REMAINING IN DECK** (").append(String.valueOf(deckCount)).append(") _").append(formatPercent.format(deckDrawChance)).append("_\n");
            for (String relicId : allRelics) {
                String[] relicData = Mapper.getRelic(relicId).split(";");
                text.append("- ").append(relicData[0]).append("\n");
            }
        }
        MessageHelper.replyToMessage(event, text.toString());
    }
}
