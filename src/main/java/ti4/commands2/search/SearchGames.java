package ti4.commands2.search;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;

class SearchGames extends Subcommand {

    public SearchGames() {
        super(Constants.SEARCH_GAMES, "List all games");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ENDED_GAMES, "True to also show ended games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.NORMAL_GAME, "True to include Normal (none of the other modes) games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.TIGL_GAME, "True to include TIGL games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.COMMUNITY_MODE, "True to include community games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ALLIANCE_MODE, "True to include alliance games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FOW_MODE, "True to include Fog of War games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ABSOL_MODE, "True to include Absol's Agenda & Relics games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DISCORDANT_STARS_MODE, "True to include Discordant Stars games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FRANKEN_MODE, "True to include Franken games"));
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH_NAMES, "Include games with this text in game name"));
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH_TAGS, "Include games with this text in game tags"));
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH_FACTIONS, "Include games with this faction in the game"));
        addOptions(new OptionData(OptionType.USER, Constants.SEARCH_USERS, "Include games with this user in the game"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean includeNormalGames = event.getOption(Constants.NORMAL_GAME, false, OptionMapping::getAsBoolean);
        boolean includeTIGLGames = event.getOption(Constants.TIGL_GAME, false, OptionMapping::getAsBoolean);
        boolean includeCommunityGames = event.getOption(Constants.COMMUNITY_MODE, false, OptionMapping::getAsBoolean);
        boolean includeAllianceGames = event.getOption(Constants.ALLIANCE_MODE, false, OptionMapping::getAsBoolean);
        boolean includeFoWGames = event.getOption(Constants.FOW_MODE, false, OptionMapping::getAsBoolean);
        boolean includeAbsolGames = event.getOption(Constants.ABSOL_MODE, false, OptionMapping::getAsBoolean);
        boolean includeMiltyModGames = event.getOption(Constants.MILTYMOD_MODE, false, OptionMapping::getAsBoolean);
        boolean includeDSGames = event.getOption(Constants.DISCORDANT_STARS_MODE, false, OptionMapping::getAsBoolean);
        boolean includeFrankenGames = event.getOption(Constants.FRANKEN_MODE, false, OptionMapping::getAsBoolean);
        boolean includeEndedGames = event.getOption(Constants.ENDED_GAMES, false, OptionMapping::getAsBoolean);
        String searchName = event.getOption(Constants.SEARCH_NAMES, "", OptionMapping::getAsString).toLowerCase();
        String searchTags = event.getOption(Constants.SEARCH_TAGS, "", OptionMapping::getAsString).toLowerCase();
        String searchFactions = event.getOption(Constants.SEARCH_FACTIONS, "", OptionMapping::getAsString).toLowerCase();
        User searchUser = event.getOption(Constants.SEARCH_USERS, null, OptionMapping::getAsUser);

        Predicate<Game> normalGamesPredicate = Game::isNormalGame;
        Predicate<Game> tIGLGamesPredicate = Game::isCompetitiveTIGLGame;
        Predicate<Game> communityGamesPredicate = Game::isCommunityMode;
        Predicate<Game> allianceGamesPredicate = Game::isAllianceMode;
        Predicate<Game> foWGamesPredicate = Game::isFowMode;
        Predicate<Game> absolGamesPredicate = Game::isAbsolMode;
        Predicate<Game> miltyModGamesPredicate = Game::isMiltyModMode;
        Predicate<Game> dSGamesPredicate = Game::isDiscordantStarsMode;
        Predicate<Game> frankenGamesPredicate = Game::isFrankenGame;
        Predicate<Game> endedGamesPredicate = Game::isHasEnded;
        Predicate<Game> searchNamePredicate = game -> game.getCustomName().toLowerCase().contains(searchName);
        Predicate<Game> searchTagPredicate = game -> game.getGameModesText().toLowerCase().contains(searchTags);
        Predicate<Game> searchFactionPredicate = game ->
            game.getRealAndEliminatedPlayers().stream()
                .map(player -> player.getFaction().toLowerCase())
                .anyMatch(faction -> faction.contains(searchFactions));
        Predicate<Game> searchUserPredicate = game -> game.hasUser(searchUser);

        AtomicInteger normalGamesCount = new AtomicInteger();
        AtomicInteger tIGLGamesCount = new AtomicInteger();
        AtomicInteger communityGamesCount = new AtomicInteger();
        AtomicInteger allianceGamesCount = new AtomicInteger();
        AtomicInteger foWGamesCount = new AtomicInteger();
        AtomicInteger absolGamesCount = new AtomicInteger();
        AtomicInteger miltyModGamesCount = new AtomicInteger();
        AtomicInteger dSGamesCount = new AtomicInteger();
        AtomicInteger frankenGamesCount = new AtomicInteger();
        AtomicInteger endedGamesCount = new AtomicInteger();
        AtomicInteger searchNameGamesCount = new AtomicInteger();
        AtomicInteger searchTagGamesCount = new AtomicInteger();
        AtomicInteger searchFactionGamesCount = new AtomicInteger();
        AtomicInteger searchUserGamesCount = new AtomicInteger();

        Map<Predicate<Game>, AtomicInteger> predicatesToCounts = new LinkedHashMap<>();
        if (includeNormalGames) predicatesToCounts.put(normalGamesPredicate, normalGamesCount);
        if (includeTIGLGames) predicatesToCounts.put(tIGLGamesPredicate, tIGLGamesCount);
        if (includeCommunityGames) predicatesToCounts.put(communityGamesPredicate, communityGamesCount);
        if (includeAllianceGames) predicatesToCounts.put(allianceGamesPredicate, allianceGamesCount);
        if (includeFoWGames) predicatesToCounts.put(foWGamesPredicate, foWGamesCount);
        if (includeAbsolGames) predicatesToCounts.put(absolGamesPredicate, absolGamesCount);
        if (includeMiltyModGames) predicatesToCounts.put(miltyModGamesPredicate, miltyModGamesCount);
        if (includeDSGames) predicatesToCounts.put(dSGamesPredicate, dSGamesCount);
        if (includeFrankenGames) predicatesToCounts.put(frankenGamesPredicate, frankenGamesCount);
        if (includeEndedGames) predicatesToCounts.put(endedGamesPredicate, endedGamesCount);
        if (!searchName.isEmpty()) predicatesToCounts.put(searchNamePredicate, searchNameGamesCount);
        if (!searchTags.isEmpty()) predicatesToCounts.put(searchTagPredicate, searchTagGamesCount);
        if (!searchFactions.isEmpty()) predicatesToCounts.put(searchFactionPredicate, searchFactionGamesCount);
        if (searchUser != null) predicatesToCounts.put(searchUserPredicate, searchUserGamesCount);

        if (predicatesToCounts.isEmpty()) {
            MessageHelper.replyToMessage(event, "You must include at least one filter.");
            return;
        }

        Set<String> filteredGameNames = new HashSet<>();
        StringBuilder gameText = new StringBuilder();
        GamesPage.consumeAllGames(
            predicatesToCounts.keySet().stream().reduce(Predicate::and).get(),
            game -> filterGamesAndBuildText(gameText, game, predicatesToCounts, filteredGameNames)
        );

        StringBuilder sb = new StringBuilder("__**Search Games:**__\n");
        if (filteredGameNames.isEmpty()) {
            sb.append("No games match the selected filters.");
        } else {
            int totalGames = GameManager.getGameCount();

            Map<Predicate<Game>, Integer> predicatesToIntegerCounts = predicatesToCounts.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));

            sb.append("-# Statistics:\n");
            sb.append("-# > Total Games: `").append(totalGames).append("`\n");
            sb.append("-# > Games Found: `").append(filteredGameNames.size()).append("`\n");
            sb.append("-# Statistics:\n");
            sb.append("-# > Total Games: `").append(totalGames).append("`\n");
            sb.append("-# > Games Found: `").append(filteredGameNames.size()).append("`\n");
            sb.append("-# > ").append(trueFalseEmoji(includeNormalGames)).append("includeNormalGames (").append(predicatesToIntegerCounts.getOrDefault(normalGamesPredicate, 0)).append("/").append(totalGames).append(")\n");
            sb.append("-# > ").append(trueFalseEmoji(includeTIGLGames)).append("includeTIGLGames (").append(predicatesToIntegerCounts.getOrDefault(tIGLGamesPredicate, 0)).append("/").append(totalGames).append(")\n");
            sb.append("-# > ").append(trueFalseEmoji(includeCommunityGames)).append("includeCommunityGames (").append(predicatesToIntegerCounts.getOrDefault(communityGamesPredicate, 0)).append("/").append(totalGames).append(")\n");
            sb.append("-# > ").append(trueFalseEmoji(includeAllianceGames)).append("includeAllianceGames (").append(predicatesToIntegerCounts.getOrDefault(allianceGamesPredicate, 0)).append("/").append(totalGames).append(")\n");
            sb.append("-# > ").append(trueFalseEmoji(includeFoWGames)).append("includeFoWGames (").append(predicatesToIntegerCounts.getOrDefault(foWGamesPredicate, 0)).append("/").append(totalGames).append(")\n");
            sb.append("-# > ").append(trueFalseEmoji(includeAbsolGames)).append("includeAbsolGames (").append(predicatesToIntegerCounts.getOrDefault(absolGamesPredicate, 0)).append("/").append(totalGames).append(")\n");
            sb.append("-# > ").append(trueFalseEmoji(includeMiltyModGames)).append("includeMiltyModGames (").append(predicatesToIntegerCounts.getOrDefault(miltyModGamesPredicate, 0)).append("/").append(totalGames).append(")\n");
            sb.append("-# > ").append(trueFalseEmoji(includeDSGames)).append("includeDSGames (").append(predicatesToIntegerCounts.getOrDefault(dSGamesPredicate, 0)).append("/").append(totalGames).append(")\n");
            sb.append("-# > ").append(trueFalseEmoji(includeFrankenGames)).append("includeFrankenGames (").append(predicatesToIntegerCounts.getOrDefault(frankenGamesPredicate, 0)).append("/").append(totalGames).append(")\n");
            sb.append("-# > ").append(trueFalseEmoji(includeEndedGames)).append("includeEndedGames (").append(predicatesToIntegerCounts.getOrDefault(endedGamesPredicate, 0)).append("/").append(totalGames).append(")\n");

            sb.append("-# > ").append(trueFalseEmoji(!searchName.isEmpty())).append("gamesWithName `").append(searchName).append("` (").append(predicatesToIntegerCounts.getOrDefault(searchNamePredicate, 0)).append("/").append(totalGames).append(")\n");
            sb.append("-# > ").append(trueFalseEmoji(!searchTags.isEmpty())).append("gamesWithTag `").append(searchTags).append("` (").append(predicatesToIntegerCounts.getOrDefault(searchTagPredicate, 0)).append("/").append(totalGames).append(")\n");
            sb.append("-# > ").append(trueFalseEmoji(!searchFactions.isEmpty())).append("gamesWithFaction `").append(searchFactions).append("` (").append(predicatesToIntegerCounts.getOrDefault(searchFactionPredicate, 0)).append("/").append(totalGames).append(")\n");
            sb.append("-# > ").append(trueFalseEmoji(searchUser != null)).append("gamesWithUser `").append(searchUser != null ? searchUser.getEffectiveName() : "").append("` (").append(predicatesToIntegerCounts.getOrDefault(searchUserPredicate, 0)).append("/").append(totalGames).append(")\n");
            sb.append(gameText);
        }

        String message = sb.toString();
        message = message.replace("``", "");
        MessageHelper.sendMessageToThread(event.getChannel(), "Search Games", message);
    }

    private static void filterGamesAndBuildText(StringBuilder stringBuilder, Game game, Map<Predicate<Game>, AtomicInteger> predicateCountMap,
                                                Set<String> filteredGameNames) {
        for (Map.Entry<Predicate<Game>, AtomicInteger> entry : predicateCountMap.entrySet()) {
            if (entry.getKey().test(game)) {
                entry.getValue().incrementAndGet();
                if (!filteredGameNames.contains(game.getName())) {
                    filteredGameNames.add(game.getName());
                    appendGameRepresentation(game, stringBuilder);
                }
            }
        }
    }

    private static String trueFalseEmoji(boolean bool) {
        return bool ? "✅" : "❌";
    }

    private static void appendGameRepresentation(Game game, StringBuilder sb) {
        sb.append("- **").append(game.getName()).append("**").append(" ");
        sb.append("`").append(game.getCreationDate()).append("`-`");
        if (game.isHasEnded() && game.getEndedDate() > 100) {
            sb.append(Helper.getDateRepresentation(game.getEndedDate()));
        } else {
            sb.append(Helper.getDateRepresentation(game.getLastModifiedDate()));
        }
        sb.append("`  ");
        for (Player player : game.getPlayers().values()) {
            if (!game.isFowMode() && player.getFaction() != null) {
                sb.append(player.getFactionEmoji());
            }
        }
        sb.append(" [").append(game.getGameModesText()).append("] ");
        if (game.isHasEnded()) sb.append(" ENDED");
        sb.append("\n");
    }
}
