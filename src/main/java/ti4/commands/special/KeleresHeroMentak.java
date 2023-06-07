package ti4.commands.special;

import java.util.ArrayList;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

public class KeleresHeroMentak extends SpecialSubcommandData {

    public KeleresHeroMentak() {
        super(Constants.KELERES_HERO_MENTAK, "Draw Action Cards until you have drawn 3 with component actions, discard the rest.");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        if (!(player.getFaction().equalsIgnoreCase("keleresm") || player.getFaction().equalsIgnoreCase("keleres"))) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player is not playing the faction *'Council of Keleres - Mentak'*");
            return;
        }
        Integer originalACDeckCount = activeMap.getActionCards().size();
        StringBuilder acRevealMessage = new StringBuilder("The following non-component action cards were revealed before drawing three component action cards:\n");
        StringBuilder acDrawMessage = new StringBuilder("The following component action cards were drawn into their hand:\n");
        ArrayList<String> cardsToShuffleBackIntoDeck = new ArrayList<>();
        Integer componentActionACCount = 0;
        Integer index = 1;
        Boolean noMoreComponentActionCards = false;
        while (componentActionACCount < 3) {
            Integer acID = null;
            String acKey = null;
            for (java.util.Map.Entry<String, Integer> ac : Helper.getLastEntryInHashMap(activeMap.drawActionCard(player.getUserID())).entrySet()) {
                acID = ac.getValue();
                acKey = ac.getKey();
            }
            ActionCardModel actionCard = Mapper.getActionCard(acKey);
            String acName = actionCard.name;
            String acWindow = actionCard.window;
            if (acWindow.equalsIgnoreCase("Action")) {
                acDrawMessage.append("> `").append(String.format("%02d", index)).append(".` ").append(actionCard.getRepresentation());
                componentActionACCount++;
            } else {
                acRevealMessage.append("> `").append(String.format("%02d", index)).append(".` ").append(Emojis.ActionCard).append(" ").append(acName).append("\n");
                activeMap.discardActionCard(player.getUserID(), acID);
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
            Integer cardID = activeMap.getDiscardActionCards().get(card);
            activeMap.shuffleActionCardBackIntoDeck(cardID);
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), Emojis.KeleresHeroHarka);
        MessageHelper.sendMessageToChannel(event.getChannel(), Helper.getPlayerRepresentation(player, activeMap) + " uses **Keleres (Mentak) Hero** to Reveal "+ Emojis.ActionCard + "Action Cards until Drawing 3 component action cards.\n");
        MessageHelper.sendMessageToChannel(event.getChannel(), acRevealMessage.toString());
        MessageHelper.sendMessageToChannel(event.getChannel(), acDrawMessage.toString());
        MessageHelper.sendMessageToChannel(event.getChannel(), "All non-component action cards have been reshuffled back into the deck.");
        if (noMoreComponentActionCards) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "**All action cards in the deck have been revealed. __No component action cards remain.__**");
        }
        BotLogger.log(event, "DEBUG: **Keleres Hero Mentak used**");
    }

    public void secondHalf(Map activeMap, Player player, GenericInteractionCreateEvent event){

        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player could not be found");
            return;
        }
        if (!(player.getFaction().equalsIgnoreCase("keleresm") || player.getFaction().equalsIgnoreCase("keleres"))) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player is not playing the faction *'Council of Keleres - Mentak'*");
            return;
        }
        Integer originalACDeckCount = activeMap.getActionCards().size();
        StringBuilder acRevealMessage = new StringBuilder("The following non-component action cards were revealed before drawing three component action cards:\n");
        StringBuilder acDrawMessage = new StringBuilder("The following component action cards were drawn into their hand:\n");
        ArrayList<String> cardsToShuffleBackIntoDeck = new ArrayList<>();
        Integer componentActionACCount = 0;
        Integer index = 1;
        Boolean noMoreComponentActionCards = false;
        while (componentActionACCount < 3) {
            Integer acID = null;
            String acKey = null;
            for (java.util.Map.Entry<String, Integer> ac : Helper.getLastEntryInHashMap(activeMap.drawActionCard(player.getUserID())).entrySet()) {
                acID = ac.getValue();
                acKey = ac.getKey();
            }
            ActionCardModel actionCard = Mapper.getActionCard(acKey);
            String acName = actionCard.name;
            String acWindow = actionCard.window;
            if (acWindow.equalsIgnoreCase("Action")) {
                acDrawMessage.append("> `").append(String.format("%02d", index)).append(".` ").append(actionCard.getRepresentation());
                componentActionACCount++;
            } else {
                acRevealMessage.append("> `").append(String.format("%02d", index)).append(".` ").append(Emojis.ActionCard).append(" ").append(acName).append("\n");
                activeMap.discardActionCard(player.getUserID(), acID);
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
            Integer cardID = activeMap.getDiscardActionCards().get(card);
            activeMap.shuffleActionCardBackIntoDeck(cardID);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), Emojis.KeleresHeroHarka);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeMap) + " uses **Keleres (Mentak) Hero** to Reveal "+ Emojis.ActionCard + "Action Cards until Drawing 3 component action cards.\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), acRevealMessage.toString());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), acDrawMessage.toString());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "All non-component action cards have been reshuffled back into the deck.");
        if (noMoreComponentActionCards) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "**All action cards in the deck have been revealed. __No component action cards remain.__**");
        }
        BotLogger.log(event, "DEBUG: **Keleres Hero Mentak used**");
    }
}
