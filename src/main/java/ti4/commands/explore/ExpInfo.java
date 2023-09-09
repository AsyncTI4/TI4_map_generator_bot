package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.StringTokenizer;

public class ExpInfo extends ExploreSubcommandData {

    public ExpInfo() {
        super(Constants.INFO, "Display cards in exploration decks and discards.");
        addOptions(typeOption);
        addOptions(new OptionData(OptionType.STRING, Constants.OVERRIDE_FOW, "TRUE if override fog"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveMap();
        ArrayList<String> types = new ArrayList<>();
        OptionMapping reqType = event.getOption(Constants.TRAIT);
        OptionMapping override = event.getOption(Constants.OVERRIDE_FOW);

        boolean over = false;
        if (override != null) {
           over = override.getAsString().equalsIgnoreCase("TRUE");
        }
        if (reqType != null) {
            types.add(reqType.getAsString());
        } else {
            types.add(Constants.CULTURAL);
            types.add(Constants.INDUSTRIAL);
            types.add(Constants.HAZARDOUS);
            types.add(Constants.FRONTIER);
        }
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        for (String currentType : types) {
            StringBuilder info = new StringBuilder();
            ArrayList<String> deck = activeGame.getExploreDeck(currentType);
            Collections.sort(deck);
            Integer deckCount = deck.size();
            Double deckDrawChance = deckCount == 0 ? 0.0 : 1.0 / deckCount;
            NumberFormat formatPercent = NumberFormat.getPercentInstance();
            formatPercent.setMaximumFractionDigits(1);
            ArrayList<String> discard = activeGame.getExploreDiscard(currentType);
            Collections.sort(discard);
            Integer discardCount = discard.size();

            info.append(Helper.getEmojiFromDiscord(currentType)).append("**").append(currentType.toUpperCase()).append(" EXPLORE DECK** (").append(String.valueOf(deckCount)).append(") _").append(formatPercent.format(deckDrawChance)).append("_\n");
            info.append(listNames(deck)).append("\n");
            info.append(Helper.getEmojiFromDiscord(currentType)).append("**").append(currentType.toUpperCase()).append(" EXPLORE DISCARD** (").append(String.valueOf(discardCount)).append(")\n");
            info.append(listNames(discard)).append("\n_ _\n");


            if (player == null || player.getSCs().isEmpty() || over || !activeGame.isFoWMode()) {
                sendMessage(info.toString());
            }
        }
        if (player != null && activeGame.getCurrentPhase().equalsIgnoreCase("action") && !over && activeGame.isFoWMode()) {
                sendMessage("It is foggy outside, please wait until status/agenda to do this command, or override the fog.");
            }
    }

    private String listNames(ArrayList<String> deck) {
        StringBuilder sb = new StringBuilder();
        for (String cardID : deck) {
            String card = Mapper.getExplore(cardID);
            String name = null;
            if (card != null) {
                StringTokenizer cardInfo = new StringTokenizer(card, ";");
                name = cardInfo.nextToken();
            }
            sb.append("(").append(cardID).append(") ").append(name).append("\n");
        }
        return sb.toString();
    }
}
