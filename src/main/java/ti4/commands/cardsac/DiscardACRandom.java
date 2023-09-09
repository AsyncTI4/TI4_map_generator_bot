package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

public class DiscardACRandom extends ACCardsSubcommandData {
    public DiscardACRandom() {
        super(Constants.DISCARD_AC_RANDOM, "Discard a random Action Card");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to discard, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
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
        if (actionCardsMap.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Action Cards in hand");
            return;
        }
        discardRandomAC(event, activeGame, player, count);
        
    }
    public void discardRandomAC(GenericInteractionCreateEvent event, Game activeGame, Player player, int count){
        if(count < 1){
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Player: ").append(player.getUserName()).append(" - ");
        sb.append("Discarded Action Card:").append("\n");
        while (count > 0 && player.getActionCards().size() > 0) {
            LinkedHashMap<String, Integer> actionCards_ = player.getActionCards();
            ArrayList<String> cards_ = new ArrayList<>(actionCards_.keySet());
            Collections.shuffle(cards_);
            String acID = cards_.get(0);
            boolean removed = activeGame.discardActionCard(player.getUserID(), actionCards_.get(acID));
            if (!removed) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No such Action Cards found, please retry");
                return;
            }
            sb.append(Mapper.getActionCard(acID).getRepresentation()).append("\n");
            count--;
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), sb.toString());
        ACInfo.sendActionCardInfo(activeGame, player);
    }
}
