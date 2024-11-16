package ti4.commands.special;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

public class KeleresHeroMentak extends SpecialSubcommandData {

    public KeleresHeroMentak() {
        super(Constants.KELERES_HERO_MENTAK, "Draw Action Cards until you have drawn 3 with component actions, discard the rest.");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        // if (!("keleresm".equalsIgnoreCase(player.getFaction()) || "keleres".equalsIgnoreCase(player.getFaction()))) {
        //     MessageHelper.sendMessageToChannel(event.getChannel(), "Player is not playing the faction *'Council of Keleres - Mentak'*");
        //     return;
        // }
        resolveKeleresHeroMentak(game, player, event);
    }

    public static void resolveKeleresHeroMentak(Game game, Player player, GenericInteractionCreateEvent event) {
        int originalACDeckCount = game.getActionCards().size();
        StringBuilder acRevealMessage = new StringBuilder("The following non-component action cards were revealed before drawing three component action cards:\n");
        StringBuilder acDrawMessage = new StringBuilder("The following component action cards were drawn into their hand:\n");
        List<String> cardsToShuffleBackIntoDeck = new ArrayList<>();
        int componentActionACCount = 0;
        int index = 1;
        boolean noMoreComponentActionCards = false;
        while (componentActionACCount < 3) {
            Integer acID = null;
            String acKey = null;
            for (Map.Entry<String, Integer> ac : Helper.getLastEntryInHashMap(game.drawActionCard(player.getUserID())).entrySet()) {
                acID = ac.getValue();
                acKey = ac.getKey();
            }
            ActionCardModel actionCard = Mapper.getActionCard(acKey);
            String acName = actionCard.getName();
            String acWindow = actionCard.getWindow();
            if ("Action".equalsIgnoreCase(acWindow)) {
                acDrawMessage.append("> `").append(String.format("%02d", index)).append(".` ").append(actionCard.getRepresentation());
                componentActionACCount++;
            } else {
                acRevealMessage.append("> `").append(String.format("%02d", index)).append(".` ").append(Emojis.ActionCard).append(" ").append(acName).append("\n");
                game.discardActionCard(player.getUserID(), acID);
                cardsToShuffleBackIntoDeck.add(acKey);
            }
            index++;
            if (index >= originalACDeckCount) {
                if (index > originalACDeckCount * 2) {
                    noMoreComponentActionCards = true;
                    break;
                }
            }
        }
        for (String card : cardsToShuffleBackIntoDeck) {
            Integer cardID = game.getDiscardActionCards().get(card);
            game.shuffleActionCardBackIntoDeck(cardID);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), Emojis.KeleresHeroHarka);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " uses Harka Leeds, the Keleres (Mentak) hero, to reveal " + Emojis.ActionCard + "action cards, until drawing 3 component action cards.\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), acRevealMessage.toString());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), acDrawMessage.toString());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "All non-component action cards have been reshuffled back into the deck.");
        ActionCardHelper.sendActionCardInfo(game, player);
        ButtonHelper.checkACLimit(game, event, player);
        if (noMoreComponentActionCards) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "**All action cards in the deck have been revealed. __No component action cards remain.__**");
        }
    }
}
