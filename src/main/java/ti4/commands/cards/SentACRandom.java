package ti4.commands.cards;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class SentACRandom extends CardsSubcommandData {
    public SentACRandom() {
        super(Constants.SEND_AC_RANDOM, "Send Action Card to player");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player to which to senb the Action Card").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        LinkedHashMap<String, Integer> actionCardsMap = player.getActionCards();
        List<String> actionCards = new ArrayList<>(actionCardsMap.keySet());
        if (actionCards.isEmpty()){
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Action Cards in hand");
        }
        Collections.shuffle(actionCards);
        String acID = actionCards.get(0);
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        if (playerOption != null) {
            User user = playerOption.getAsUser();
            Player targetPlayer = activeMap.getPlayer(user.getId());
            if (targetPlayer == null){
                MessageHelper.sendMessageToChannel(event.getChannel(), "No such Player in game");
                return;
            }

            player.removeActionCard(actionCardsMap.get(acID));
            targetPlayer.setActionCard(acID);
            CardsInfo.sentUserCardInfo(event, activeMap, targetPlayer);
            CardsInfo.sentUserCardInfo(event, activeMap, player);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Need to specify player");
            return;
        }

    }
}
