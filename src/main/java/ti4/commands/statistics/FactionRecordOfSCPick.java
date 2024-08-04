package ti4.commands.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

public class FactionRecordOfSCPick extends StatisticsSubcommandData {

    private static final String PLAYER_COUNT_FILTER = "player_count";
    private static final String VICTORY_POINT_GOAL_FILTER = "victory_point_goal";
    private static final String GAME_TYPE_FILTER = "game_type";
    private static final String FOG_FILTER = "is_fog";
    private static final String HOMEBREW_FILTER = "has_homebrew";
    private static final String FACTION_WON_FILTER = "faction_won";

    public FactionRecordOfSCPick() {
        super(Constants.FACTION_RECORD_OF_SCPICK, "Number of times a technology has been acquired by a faction.");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction that you want the technology history of").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, PLAYER_COUNT_FILTER, "Filter by player count, e.g. 3-8"));
        addOptions(new OptionData(OptionType.INTEGER, VICTORY_POINT_GOAL_FILTER, "Filter by victory point goal, e.g. 10-14"));
        addOptions(new OptionData(OptionType.STRING, GAME_TYPE_FILTER, "Filter by game type, e.g. base, PoK, absol, DS, action_deck_2, little_omega"));
        addOptions(new OptionData(OptionType.BOOLEAN, FOG_FILTER, "Filter by if the game is a fog of war game"));
        addOptions(new OptionData(OptionType.BOOLEAN, HOMEBREW_FILTER, "Filter by if the game has any homebrew"));
        addOptions(new OptionData(OptionType.BOOLEAN, FACTION_WON_FILTER, "Only include games where the faction won"));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String text = "";
        for (int x = 1; x < 7; x++) {
            text = text + getSCPick(event, x);
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "SC Pick Record", text);
    }

    private String getSCPick(SlashCommandInteractionEvent event, int round) {
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event.getOption(PLAYER_COUNT_FILTER, null, OptionMapping::getAsInt), event.getOption(VICTORY_POINT_GOAL_FILTER, null, OptionMapping::getAsInt),
            event.getOption(GAME_TYPE_FILTER, null, OptionMapping::getAsString), event.getOption(FOG_FILTER, null, OptionMapping::getAsBoolean), event.getOption(HOMEBREW_FILTER, null, OptionMapping::getAsBoolean), false);
        String faction = event.getOption(Constants.FACTION, "eh", OptionMapping::getAsString);
        FactionModel factionM = Mapper.getFaction(faction);
        if (factionM == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No faction known as " + faction + ".");
            return "bleh";
        }
        boolean onlyIncludeWins = event.getOption(FACTION_WON_FILTER, false, OptionMapping::getAsBoolean);
        if (onlyIncludeWins) {
            filteredGames = filteredGames.stream()
                .filter(game -> game.getWinner().get().getFaction().equalsIgnoreCase(faction))
                .toList();
        }
        Map<String, Integer> scsPicked = new HashMap<>();
        int gamesThatHadThem = 0;

        for (Game game : filteredGames) {
            for (Player player : game.getRealPlayers()) {
                String scs = game.getStoredValue("Round" + round + "SCPickFor" + faction);
                if (player.getFaction().equalsIgnoreCase(faction) && !scs.isEmpty()) {
                    gamesThatHadThem++;
                    String[] scList = scs.split("_");
                    for (String sc : scList) {
                        sc = game.getStrategyCardModelByInitiative(Integer.parseInt(sc)).get().getName();
                        if (scsPicked.containsKey(sc)) {
                            scsPicked.put(sc, scsPicked.get(sc) + 1);
                        } else {
                            scsPicked.put(sc, 1);
                        }

                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();

        sb.append("## __**SCs Picked By " + factionM.getFactionName() + " In Round #" + round + " (From " + gamesThatHadThem + " Games)**__\n");

        boolean sortOrderAscending = event.getOption("ascending", false, OptionMapping::getAsBoolean);
        Comparator<Entry<String, Integer>> comparator = (o1, o2) -> {
            int o1total = o1.getValue();
            int o2total = o2.getValue();
            return sortOrderAscending ? Integer.compare(o1total, o2total) : -Integer.compare(o1total, o2total);
        };

        AtomicInteger index = new AtomicInteger(1);

        scsPicked.entrySet().stream()
            .sorted(comparator)
            .forEach(techResearched -> {

                sb.append("`").append(Helper.leftpad(String.valueOf(index.get()), 3)).append(". ");
                sb.append("` ").append(techResearched.getKey());
                sb.append(": " + techResearched.getValue());
                sb.append("\n");
                index.getAndIncrement();
            });

        return sb.toString();
    }
}
