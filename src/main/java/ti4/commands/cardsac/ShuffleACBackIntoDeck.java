package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ShuffleACBackIntoDeck extends ACCardsSubcommandData {
    public ShuffleACBackIntoDeck() {
        super(Constants.SHUFFLE_AC_BACK_INTO_DECK, "Shuffle Action Card back into deck from the discard pile.");
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        OptionMapping option = event.getOption(Constants.ACTION_CARD_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Action Card to draw from discard pile");
            return;
        }

        int acIndex = option.getAsInt();
        String acID = null;
        for (java.util.Map.Entry<String, Integer> so : activeGame.getDiscardActionCards().entrySet()) {
            if (so.getValue().equals(acIndex)) {
                acID = so.getKey();
            }
        }
        if (acID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }
        boolean picked = activeGame.shuffleActionCardBackIntoDeck(acIndex);
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(activeGame.getName()).append(" ");
        sb.append("Player: ").append(getUser().getName()).append("\n");
        sb.append("Card shuffled back into deck from discards: ");
        sb.append(Mapper.getActionCard(acID).getRepresentation()).append("\n");
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
