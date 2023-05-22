package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.StringTokenizer;

public class ExploreLookAtTop extends ExploreSubcommandData {

    public ExploreLookAtTop() {
        super(Constants.LOOK_AT_TOP, "Look at the top card of an explore deck. Sends to Cards Info thread.");
        addOptions(typeOption.setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        String trait = event.getOption(Constants.TRAIT, null, OptionMapping::getAsString);
        if (trait == null || trait.isEmpty() || trait.isBlank()) {
            sendMessage("Trait not found");
            return;
        }
        // KEEPING CODE INCASE NEED TO SEE ALL OR MORE AT ONCE
        // ArrayList<String> traitsToLookAt = new ArrayList<String>();
        // OptionMapping reqType = event.getOption(Constants.TRAIT);
        // if (reqType != null) {
        //     traitsToLookAt.add(reqType.getAsString());
        // }
        // else {
        //     traitsToLookAt.add(Constants.CULTURAL);
        //     traitsToLookAt.add(Constants.INDUSTRIAL);
        //     traitsToLookAt.add(Constants.HAZARDOUS);
        //     traitsToLookAt.add(Constants.FRONTIER);
        // }
        // for (String currentType : traitsToLookAt) {
        //     StringBuilder info = new StringBuilder();
        //     ArrayList<String> deck = activeMap.getExploreDeck(currentType);
        //     Collections.sort(deck);
        //     Integer deckCount = deck.size();
        //     Double deckDrawChance = deckCount == 0 ? 0.0 : 1.0 / deckCount;
        //     NumberFormat formatPercent = NumberFormat.getPercentInstance();
        //     formatPercent.setMaximumFractionDigits(1);
        //     ArrayList<String> discard = activeMap.getExploreDiscard(currentType);
        //     Collections.sort(discard);
        //     Integer discardCount = discard.size();

        //     info.append(Helper.getEmojiFromDiscord(currentType)).append("**").append(currentType.toUpperCase()).append(" EXPLORE DECK** (").append(String.valueOf(deckCount)).append(") _").append(formatPercent.format(deckDrawChance)).append("_\n");
        //     info.append(listNames(deck)).append("\n");
        //     info.append(Helper.getEmojiFromDiscord(currentType)).append("**").append(currentType.toUpperCase()).append(" EXPLORE DISCARD** (").append(String.valueOf(discardCount)).append(")\n");
        //     info.append(listNames(discard)).append("\n_ _\n");
        //     sendMessage(info.toString());
        // }

        ArrayList<String> deck = activeMap.getExploreDeck(trait);
        if (deck.isEmpty()) {
            sendMessage("Explore deck is empty");
            //reshuffle discards into deck
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("__**Look at Top of " + Helper.getEmojiFromDiscord(trait) + trait + " Deck**__\n");
        String topCard = deck.get(0);
        sb.append(displayExplore(topCard));

        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, sb.toString());

    }
}
