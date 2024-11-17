package ti4.commands.statistics;

import java.util.ArrayList;
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

class FactionRecordOfTech extends Subcommand {

    private static final String FACTION_WON_FILTER = "faction_won";

    public FactionRecordOfTech() {
        super(Constants.FACTION_RECORD_OF_TECH, "# of times a tech has been acquired by a faction");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction That You Want Tech History Of").setRequired(true).setAutoComplete(true));
        addOptions(GameStatisticFilterer.gameStatsFilters());
        addOptions(new OptionData(OptionType.BOOLEAN, FACTION_WON_FILTER, "Only include games where the faction won"));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String text = getTechResearched(event);
        MessageHelper.sendMessageToThread(event.getChannel(), "Tech Acquisition Record", text);
    }

    private String getTechResearched(SlashCommandInteractionEvent event) {
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
        Map<String, Integer> techsResearched = new HashMap<>();
        int gamesThatHadThem = 0;

        for (Game game : filteredGames) {
            for (Player player : game.getRealPlayers()) {
                if (player.getFaction().equalsIgnoreCase(faction)) {
                    gamesThatHadThem++;
                    for (String tech : player.getTechs()) {
                        List<String> startingTech = new ArrayList<>();
                        if (factionM.getStartingTech() != null) {
                            startingTech = factionM.getStartingTech();
                        }
                        if (startingTech != null && !startingTech.contains(tech)) {
                            String techName = Mapper.getTech(tech).getName();
                            if (techsResearched.containsKey(techName)) {
                                techsResearched.put(techName, techsResearched.get(techName) + 1);
                            } else {
                                techsResearched.put(techName, 1);
                            }
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();

        sb.append("## __**Techs Researched By ").append(factionM.getFactionName()).append(" (From ").append(gamesThatHadThem).append(" Games)**__\n");

        boolean sortOrderAscending = event.getOption("ascending", false, OptionMapping::getAsBoolean);
        Comparator<Entry<String, Integer>> comparator = (o1, o2) -> {
            int o1total = o1.getValue();
            int o2total = o2.getValue();
            return sortOrderAscending ? Integer.compare(o1total, o2total) : -Integer.compare(o1total, o2total);
        };

        AtomicInteger index = new AtomicInteger(1);

        techsResearched.entrySet().stream()
            .sorted(comparator)
            .forEach(techResearched -> {
                sb.append("`").append(Helper.leftpad(String.valueOf(index.get()), 3)).append(". ");
                sb.append("` ").append(techResearched.getKey());
                sb.append(": ").append(techResearched.getValue());
                sb.append("\n");
                index.getAndIncrement();
            });

        return sb.toString();
    }
}
