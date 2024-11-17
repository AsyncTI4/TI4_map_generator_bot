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
import ti4.commands2.Subcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

class FactionRecordOfSCPick extends Subcommand {

    private static final String FACTION_WON_FILTER = "faction_won";

    public FactionRecordOfSCPick() {
        super(Constants.FACTION_RECORD_OF_SCPICK, "# of times an SC has been picked by a faction, by round");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction That You Want History Of").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, FACTION_WON_FILTER, "Only include games where the faction won"));
        addOptions(GameStatisticFilterer.gameStatsFilters());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        StringBuilder text = new StringBuilder();
        for (int x = 1; x < 7; x++) {
            text.append(getSCPick(event, x));
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "SC Pick Record", text.toString());
    }

    private String getSCPick(SlashCommandInteractionEvent event, int round) {
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
        String faction = event.getOption(Constants.FACTION, "eh", OptionMapping::getAsString);
        FactionModel factionM = Mapper.getFaction(faction);
        if (factionM == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No faction known as " + faction);
            return "bleh";
        }
        boolean onlyIncludeWins = event.getOption(FACTION_WON_FILTER, false, OptionMapping::getAsBoolean);
        if (onlyIncludeWins) {
            filteredGames = filteredGames.stream()
                .filter(game -> game.getWinner().get().getFaction().equalsIgnoreCase(faction))
                .toList();
        }
        Map<String, Integer> scsPicked = new HashMap<>();
        Map<String, Integer> custodians = new HashMap<>();
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
                        if (game.getCustodiansTaker() != null && game.getCustodiansTaker().equalsIgnoreCase(faction)) {
                            if (custodians.containsKey(sc)) {
                                custodians.put(sc, custodians.get(sc) + 1);
                            } else {
                                custodians.put(sc, 1);
                            }
                        }

                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();

        sb.append("## __**SCs Picked By ").append(factionM.getFactionName()).append(" In Round #").append(round).append(" (From ").append(gamesThatHadThem).append(" Games)**__\n");

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
                sb.append(": ").append(techResearched.getValue());
                if (round == 1) {
                    sb.append(" (Took Custodians a total of  ").append(custodians.getOrDefault(techResearched.getKey(), 0)).append(" times)");
                }
                sb.append("\n");
                index.getAndIncrement();
            });

        return sb.toString();
    }
}
