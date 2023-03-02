package ti4.commands.special;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

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
        player = Helper.getPlayer(activeMap, player, event);
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        if (!player.getFaction().equalsIgnoreCase("keleres")) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player is not playing the faction *'Council of Keleres'*");
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
            String[] actionCardData = Mapper.getActionCard(acKey).split(";");
            String acName = actionCardData[0];
            String acPhase = actionCardData[1];
            String acWindow = actionCardData[2];
            String acDescription = actionCardData[3];
            if (acWindow.equalsIgnoreCase("Action")) {
                acDrawMessage.append("> `").append(String.format("%02d", index)).append(".` ").append(Emojis.ActionCard).append("Action Card: __**").append(acName).append("**__: *").append(acWindow).append(":* ").append(acDescription).append("\n");
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
        MessageHelper.sendMessageToChannel(event.getChannel(), Helper.getPlayerRepresentation(event, player) + " uses **Keleres (Mentak) Hero** to Reveal "+ Emojis.ActionCard + "Action Cards until Drawing 3 component action cards.\n");
        MessageHelper.sendMessageToChannel(event.getChannel(), acRevealMessage.toString());
        MessageHelper.sendMessageToChannel(event.getChannel(), acDrawMessage.toString());
        MessageHelper.sendMessageToChannel(event.getChannel(), "All non-component action cards have been reshuffled back into the deck.");
        if (noMoreComponentActionCards) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "**All action cards in the deck have been revealed. __No component action cards remain.__**");
        }
        BotLogger.log(event, "DEBUG: **Keleres Hero Mentak used**");
    }
} 
