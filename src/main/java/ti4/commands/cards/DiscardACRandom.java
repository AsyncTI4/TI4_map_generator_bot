package ti4.commands.cards;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.*;

public class DiscardACRandom extends CardsSubcommandData {
    public DiscardACRandom() {
        super(Constants.DISCARD_AC_RANDOM, "Discard Random Action Card");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping option = event.getOption(Constants.COUNT);
        int count = 1;
        if (option != null) {
            int providedCount = option.getAsInt();
            count = providedCount > 0 ? providedCount : 1;
        }

        LinkedHashMap<String, Integer> actionCardsMap = player.getActionCards();
        List<String> actionCards = new ArrayList<>(actionCardsMap.keySet());
        if (actionCards.isEmpty()){
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Action Cards in hand");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Player: ").append(player.getUserName()).append(" - ");
        sb.append("Discarded Action Card:").append("\n");
        for (int i = 0; i < count; i++) {
            if (actionCards.size() > 0) {
                Collections.shuffle(actionCards);
                String acID = actionCards.get(0);
                boolean removed = activeMap.discardActionCard(player.getUserID(), actionCardsMap.get(acID));
                if (!removed) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Cards found, please retry");
                    return;
                }
                sb.append(Mapper.getActionCard(acID)).append("\n");
            }
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        CardsInfo.sentUserCardInfo(event, activeMap, player);
    }
}
