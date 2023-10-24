package ti4.commands.explore;

import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.ExploreModel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

public class ExpInfo extends ExploreSubcommandData {

    public ExpInfo() {
        super(Constants.INFO, "Display cards in exploration decks and discards.");
        addOptions(typeOption);
        addOptions(new OptionData(OptionType.STRING, Constants.OVERRIDE_FOW, "TRUE if override fog"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        List<String> types = new ArrayList<>();
        OptionMapping reqType = event.getOption(Constants.TRAIT);
        OptionMapping override = event.getOption(Constants.OVERRIDE_FOW);

        boolean over = false;
        if (override != null) {
            over = "TRUE".equalsIgnoreCase(override.getAsString());
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

            info.append(Emojis.getEmojiFromDiscord(currentType)).append("**").append(currentType.toUpperCase()).append(" EXPLORE DECK** (")
                .append(deckCount).append(") _").append(formatPercent.format(deckDrawChance)).append("_\n");
            info.append(listNames(deck, true)).append("\n");

            info.append(Emojis.getEmojiFromDiscord(currentType)).append("**").append(currentType.toUpperCase()).append(" EXPLORE DISCARD** (")
                .append(discardCount).append(")\n");
            info.append(listNames(discard, false));

            if (types.indexOf(currentType) != types.size() - 1) {
                info.append("​"); // add a zero width space at the end to cement newlines between sets of explores
            }

            if (player == null || player.getSCs().isEmpty() || over || !activeGame.isFoWMode()) {
                sendMessage(info.toString());
            }
        }
        if (player != null && "action".equalsIgnoreCase(activeGame.getCurrentPhase()) && !over && activeGame.isFoWMode()) {
            sendMessage("It is foggy outside, please wait until status/agenda to do this command, or override the fog.");
        }
    }

    private String listNames(List<String> deck, boolean showPercents) {
        Integer deckCount = deck.size();
        Double deckDrawChance = deckCount == 0 ? 0.0 : 1.0 / deckCount;
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        formatPercent.setMaximumFractionDigits(1);

        StringBuilder sb = new StringBuilder();
        if (deck.isEmpty()) {
            sb.append("> there is nothing here\n");
        }

        Map<String, List<ExploreModel>> explores = deck.stream().map(Mapper::getExplore).filter(e -> e != null)
            .collect(Collectors.groupingBy(exp -> exp.getName()));
        List<Map.Entry<String, List<ExploreModel>>> orderedExplores = explores.entrySet().stream()
            .sorted(Comparator.comparingInt(e -> 15 - e.getValue().size())).toList();
        for (Map.Entry<String, List<ExploreModel>> entry : orderedExplores) {
            String exploreName = entry.getKey();
            List<String> ids = entry.getValue().stream().map(ExploreModel::getId).toList();
            sb.append("> ").append(exploreName).append(" [").append(String.join(", ", ids)).append("]");
            if (showPercents && ids.size() > 1) {
                sb.append(" _").append(formatPercent.format(deckDrawChance * ids.size())).append("_");
            }
            sb.append("\n");
        }

        List<String> unmapped = deck.stream().filter(e -> Mapper.getExplore(e) == null).toList();
        for (String cardID : unmapped) {
            String card = Mapper.getExploreRepresentation(cardID);
            String name = null;
            if (card != null) {
                StringTokenizer cardInfo = new StringTokenizer(card, ";");
                name = cardInfo.nextToken();
            }
            sb.append("> (").append(cardID).append(") ").append(name).append("\n");
        }
        return sb.toString();
    }
}
