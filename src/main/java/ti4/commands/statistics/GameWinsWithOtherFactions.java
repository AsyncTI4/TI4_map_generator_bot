package ti4.commands.statistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.Subcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

class GameWinsWithOtherFactions extends Subcommand {

    private static final String PLAYER_COUNT_FILTER = "player_count";
    private static final String VICTORY_POINT_GOAL_FILTER = "victory_point_goal";
    private static final String GAME_TYPE_FILTER = "game_type";
    private static final String FOG_FILTER = "is_fog";
    private static final String HOMEBREW_FILTER = "has_homebrew";

    public GameWinsWithOtherFactions() {
        super(Constants.GAMES_WITH_FACTIONS, "Game Wins With Certain Factions In Them");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction That You Want In The Games").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION2, "Faction That You Want In The Games").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION3, "Faction That You Want In The Games").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION4, "Faction That You Want In The Games").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION5, "Faction That You Want In The Games").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION6, "Faction That You Want In The Games").setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, PLAYER_COUNT_FILTER, "Filter by player count, e.g. 3-8"));
        addOptions(new OptionData(OptionType.INTEGER, VICTORY_POINT_GOAL_FILTER, "Filter by victory point goal, e.g. 10-14"));
        addOptions(new OptionData(OptionType.STRING, GAME_TYPE_FILTER, "Filter by game type, e.g. base, pok, absol, ds, action_deck_2, little_omega"));
        addOptions(new OptionData(OptionType.BOOLEAN, FOG_FILTER, "Filter by if the game is a fog game"));
        addOptions(new OptionData(OptionType.BOOLEAN, HOMEBREW_FILTER, "Filter by if the game has any homebrew"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        showFactionWinPercent(event);

    }

    private static void showFactionWinPercent(SlashCommandInteractionEvent event) {
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
        Map<String, Integer> factionWinCount = new HashMap<>();
        Map<String, Integer> factionGameCount = new HashMap<>();
        List<String> reqFactions = new ArrayList<>();
        reqFactions.add(event.getOption("faction").getAsString());
        for (int x = 2; x < 7; x++) {
            OptionMapping option = event.getOption("faction" + x);
            if (option != null) {
                reqFactions.add(option.getAsString());
            }
        }

        for (Game game : filteredGames) {
            Optional<Player> winner = game.getWinner();
            if (winner.isEmpty()) {
                continue;
            }
            boolean count = true;
            List<String> factions = new ArrayList<>();
            for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
                factions.add(player.getFaction());
            }
            for (String faction : reqFactions) {
                if (!factions.contains(faction)) {
                    count = false;
                    break;
                }
            }
            if (!count) {
                continue;
            }
            String winningFaction = winner.get().getFaction();
            factionWinCount.put(winningFaction,
                1 + factionWinCount.getOrDefault(winningFaction, 0));
            game.getRealAndEliminatedAndDummyPlayers().forEach(player -> {
                String faction = player.getFaction();
                factionGameCount.put(faction,
                    1 + factionGameCount.getOrDefault(faction, 0));
            });
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Faction Win Percent:").append("\n");

        Mapper.getFactions().stream()
            .map(faction -> {
                double winCount = factionWinCount.getOrDefault(faction.getAlias(), 0);
                double gameCount = factionGameCount.getOrDefault(faction.getAlias(), 0);
                return Map.entry(faction, gameCount == 0 ? 0 : Math.round(100 * winCount / gameCount));
            })
            .filter(entry -> factionGameCount.containsKey(entry.getKey().getAlias()))
            .sorted(Map.Entry.<FactionModel, Long>comparingByValue().reversed())
            .forEach(entry -> sb.append("`")
                .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                .append("%` (")
                .append(factionGameCount.getOrDefault(entry.getKey().getAlias(), 0))
                .append(" games) ")
                .append(entry.getKey().getFactionEmoji()).append(" ")
                .append(entry.getKey().getFactionNameWithSourceEmoji())
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Faction Win Percent", sb.toString());
    }

}
