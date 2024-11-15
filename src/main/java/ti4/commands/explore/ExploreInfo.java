package ti4.commands.explore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

public class ExploreInfo extends ExploreSubcommandData {

    public ExploreInfo() {
        super(Constants.INFO, "Display cards in exploration decks and discards.");
        addOptions(typeOption);
        addOptions(new OptionData(OptionType.STRING, Constants.OVERRIDE_FOW, "TRUE if override fog"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
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
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        secondHalfOfExpInfo(types, event, player, game, over);
    }

    public static void secondHalfOfExpInfo(List<String> types, GenericInteractionCreateEvent event, Player player, Game game, boolean overRide) {
        secondHalfOfExpInfo(types, event, player, game, overRide, false);
    }

    public static void secondHalfOfExpInfo(List<String> types, GenericInteractionCreateEvent event, Player player, Game game, boolean overRide, boolean fullText) {
        for (String currentType : types) {
            StringBuilder info = new StringBuilder();
            List<String> deck = game.getExploreDeck(currentType);
            Collections.sort(deck);
            Integer deckCount = deck.size();
            Double deckDrawChance = deckCount == 0 ? 0.0 : 1.0 / deckCount;
            NumberFormat formatPercent = NumberFormat.getPercentInstance();
            formatPercent.setMaximumFractionDigits(1);
            List<String> discard = game.getExploreDiscard(currentType);
            Collections.sort(discard);
            Integer discardCount = discard.size();

            info.append(Emojis.getEmojiFromDiscord(currentType)).append("**").append(currentType.toUpperCase()).append(" EXPLORE DECK** (")
                .append(deckCount).append(") _").append(formatPercent.format(deckDrawChance)).append("_\n");
            info.append(listNames(deck, true, fullText)).append("\n");

            info.append(Emojis.getEmojiFromDiscord(currentType)).append("**").append(currentType.toUpperCase()).append(" EXPLORE DISCARD** (")
                .append(discardCount).append(")\n");
            info.append(listNames(discard, false, fullText));

            if (types.indexOf(currentType) != types.size() - 1) {
                info.append("​"); // add a zero width space at the end to cement newlines between sets of explores
            }

            if (player == null || player.getSCs().isEmpty() || overRide || !game.isFowMode()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), info.toString());
            }
        }
        if (player != null && "action".equalsIgnoreCase(game.getPhaseOfGame()) && game.isFowMode() && !overRide) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "It is foggy outside, please wait until status/agenda to do this command, or override the fog.");
        }
    }

    private static String listNames(List<String> deck, boolean showPercents, boolean showFullText) {
        int deckCount = deck.size();
        double deckDrawChance = deckCount == 0 ? 0.0 : 1.0 / deckCount;
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        formatPercent.setMaximumFractionDigits(1);

        StringBuilder sb = new StringBuilder();
        if (deck.isEmpty()) {
            sb.append("> there is nothing here\n");
        }

        Map<String, List<ExploreModel>> explores = deck.stream().map(Mapper::getExplore).filter(Objects::nonNull)
            .collect(Collectors.groupingBy(ExploreModel::getName));
        List<Map.Entry<String, List<ExploreModel>>> orderedExplores = explores.entrySet().stream()
            .sorted(Comparator.comparingInt(e -> 15 - e.getValue().size())).toList();
        for (Map.Entry<String, List<ExploreModel>> entry : orderedExplores) {
            String exploreName = entry.getKey();
            List<String> ids = entry.getValue().stream().map(ExploreModel::getId).toList();

            if (showFullText) {
                sb.append("> ").append(exploreName).append("\n").append(entry.getValue().getFirst().getText()).append(" [").append(String.join(", ", ids)).append("]");
            } else {
                sb.append("> ").append(exploreName).append(" [").append(String.join(", ", ids)).append("]");
            }

            if (showPercents && ids.size() > 1) {
                sb.append(" _").append(formatPercent.format(deckDrawChance * ids.size())).append("_");
            }
            sb.append("\n");
        }

        List<String> unmapped = deck.stream().filter(e -> Mapper.getExplore(e) == null).toList();
        for (String cardID : unmapped) {
            ExploreModel card = Mapper.getExplore(cardID);
            String name = card != null ? card.getName() : null;
            sb.append("> (").append(cardID).append(") ").append(name).append("\n");
        }
        return sb.toString();
    }
}
