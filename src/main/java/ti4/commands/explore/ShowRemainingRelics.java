package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Player;
import ti4.helpers.Helper;
import ti4.map.Map;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class ShowRemainingRelics extends GenericRelicAction {

    public ShowRemainingRelics() {
        super(Constants.RELIC_SHOW_REMAINING, "Show remaining relics in deck");
        addOptions(new OptionData(OptionType.STRING, Constants.OVERRIDE_FOW, "TRUE if override fog"));
    }

    @Override
    public void doAction(Player player, SlashCommandInteractionEvent event) {
        List<String> allRelics = new ArrayList<>(getActiveMap().getAllRelics());
        allRelics.remove(Constants.ENIGMATIC_DEVICE);
        Map activeMap = getActiveMap();
        Integer deckCount = allRelics.size();
        Double deckDrawChance = deckCount == 0 ? 0.0 : 1.0 / deckCount;
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        formatPercent.setMaximumFractionDigits(1);
        OptionMapping override = event.getOption(Constants.OVERRIDE_FOW);
        boolean over = false;
        if (override != null)
        {
           over = override.getAsString().equalsIgnoreCase("TRUE");
        }
        StringBuilder text;
        if (allRelics.isEmpty()) {
            text = new StringBuilder("**RELIC DECK IS EMPTY**");
        } else {
            text = new StringBuilder(Emojis.Relic).append(" **RELICS REMAINING IN DECK** (").append(String.valueOf(deckCount)).append(") _").append(formatPercent.format(deckDrawChance)).append("_\n");
            Collections.sort(allRelics);
            for (String relicId : allRelics) {
                String[] relicData = Mapper.getRelic(relicId).split(";");
                text.append("- ").append(relicData[0]).append("\n");
            }
        }
        Player player2 = activeMap.getPlayer(getUser().getId());
        player2 = Helper.getGamePlayer(activeMap, player2, event, null);

        if (player != null && !player.getSCs().isEmpty() && !over && activeMap.isFoWMode())
        {
                sendMessage("It is foggy outside, please wait until status/agenda to do this command, or override the fog.");
        }
        else
        {
                sendMessage(text.toString());
        }
        
    }
}
