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

public class ShuffleACBackIntoDeck extends CardsSubcommandData {
    public ShuffleACBackIntoDeck() {
        super(Constants.SHUFFLE_AC_BACK_INTO_DECK, "Shuffle Action Card back into deck");
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.ACTION_CARD_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Action Card to draw from discard pile");
            return;
        }

        int acIndex = option.getAsInt();
        String acID = null;
        for (java.util.Map.Entry<String, Integer> so : activeMap.getDiscardActionCards().entrySet()) {
            if (so.getValue().equals(acIndex)) {
                acID = so.getKey();
            }
        }
        if (acID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }
        boolean picked = activeMap.shuffleActionCardBackIntoDeck(acIndex);
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(activeMap.getName()).append(" ");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Card shuffled back into deck from discards: ");
        sb.append(Mapper.getActionCard(acID)).append("\n");
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
