package ti4.commands.statistics;

import static org.apache.commons.collections4.CollectionUtils.exists;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

public class FactionRecordOfTech extends StatisticsSubcommandData {

    private static final String PLAYER_COUNT_FILTER = "player_count";
    private static final String VICTORY_POINT_GOAL_FILTER = "victory_point_goal";
    private static final String GAME_TYPE_FILTER = "game_type";
    private static final String FOG_FILTER = "is_fog";
    private static final String HOMEBREW_FILTER = "has_homebrew";
    private static final String FACTION_WON_FILTER = "faction_won";

    public FactionRecordOfTech() {
        super(Constants.FACTION_RECORD_OF_TECH, "# of times a tech has been acquired by a faction");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction That You Want Tech History Of").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, PLAYER_COUNT_FILTER, "Filter by player count, e.g. 3-8"));
        addOptions(new OptionData(OptionType.INTEGER, VICTORY_POINT_GOAL_FILTER, "Filter by victory point goal, e.g. 10-14"));
        addOptions(new OptionData(OptionType.STRING, GAME_TYPE_FILTER, "Filter by game type, e.g. base, pok, absol, ds, action_deck_2, little_omega"));
        addOptions(new OptionData(OptionType.BOOLEAN, FOG_FILTER, "Filter by if the game is a fog game"));
        addOptions(new OptionData(OptionType.BOOLEAN, HOMEBREW_FILTER, "Filter by if the game has any homebrew"));
        addOptions(new OptionData(OptionType.BOOLEAN, FACTION_WON_FILTER, "Only include games where the faction won"));
       
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String text = getTechResearched(event);
        MessageHelper.sendMessageToThread(event.getChannel(), "Tech Acquisition Record", text);
    }

    private String getTechResearched(SlashCommandInteractionEvent event) {
         List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event.getOption(PLAYER_COUNT_FILTER, null, OptionMapping::getAsInt),event.getOption(VICTORY_POINT_GOAL_FILTER, null, OptionMapping::getAsInt),
         event.getOption(GAME_TYPE_FILTER, null, OptionMapping::getAsString), event.getOption(FOG_FILTER, null, OptionMapping::getAsBoolean),event.getOption(HOMEBREW_FILTER, null, OptionMapping::getAsBoolean), true);
        String faction = event.getOption(Constants.FACTION, "eh", OptionMapping::getAsString);
        FactionModel factionM = Mapper.getFaction(faction);
        if(factionM == null){
            MessageHelper.sendMessageToChannel(event.getChannel(), "No faction known as "+faction);
            return "bleh";
        }
        boolean onlyIncludeWins = event.getOption(FACTION_WON_FILTER, false, OptionMapping::getAsBoolean);
        if(onlyIncludeWins){
            filteredGames = filteredGames.stream()
            .filter(game -> game.getWinner().get().getFaction().equalsIgnoreCase(faction))
            .toList();
        }
        Map<String, Integer> techsResearched = new HashMap<>();
        int gamesThatHadThem = 0;


       
        for (Game game : filteredGames) {
            for (Player player : game.getRealPlayers()) {
                if(player.getFaction().equalsIgnoreCase(faction)){
                    gamesThatHadThem++;
                    for(String tech : player.getTechs()){
                        if(!factionM.getStartingTech().contains(tech)){
                            String techName = Mapper.getTech(tech).getName();
                            if(techsResearched.containsKey(techName)){
                                techsResearched.put(techName, techsResearched.get(techName)+1);
                            }else{
                                techsResearched.put(techName, 1);
                            }
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();

        sb.append("## __**Techs Researched (From "+gamesThatHadThem+" Games)**__\n");

        boolean sortOrderAscending = event.getOption("ascending", false, OptionMapping::getAsBoolean);
        Comparator<Entry<String, Integer>>  comparator = (o1, o2) -> {
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
            sb.append(": "+techResearched.getValue());
            sb.append("\n");
            index.getAndIncrement();
        });

        return sb.toString();
    }
}
