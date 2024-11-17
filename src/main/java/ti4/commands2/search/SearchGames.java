package ti4.commands2.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SearchGames extends Subcommand {

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

        Map<String, Game> games = GameManager.getGameNameToGame();
        List<Entry<String, Game>> normalGames = games.entrySet().stream().filter(map -> map.getValue().isNormalGame()).toList();
        List<Entry<String, Game>> tIGLGames = games.entrySet().stream().filter(map -> map.getValue().isCompetitiveTIGLGame()).toList();
        List<Entry<String, Game>> communityGames = games.entrySet().stream().filter(map -> map.getValue().isCommunityMode()).toList();
        List<Entry<String, Game>> allianceGames = games.entrySet().stream().filter(map -> map.getValue().isAllianceMode()).toList();
        List<Entry<String, Game>> foWGames = games.entrySet().stream().filter(map -> map.getValue().isFowMode()).toList();
        List<Entry<String, Game>> absolGames = games.entrySet().stream().filter(map -> map.getValue().isAbsolMode()).toList();
        List<Entry<String, Game>> miltyModGames = games.entrySet().stream().filter(map -> map.getValue().isMiltyModMode()).toList();
        List<Entry<String, Game>> dSGames = games.entrySet().stream().filter(map -> map.getValue().isDiscordantStarsMode()).toList();
        List<Entry<String, Game>> frankenGames = games.entrySet().stream().filter(map -> map.getValue().isFrankenGame()).toList();
        List<Entry<String, Game>> endedGames = games.entrySet().stream().filter(map -> map.getValue().isHasEnded()).toList();
        List<Entry<String, Game>> searchNameGames = games.entrySet().stream().filter(map -> map.getValue().getCustomName().toLowerCase().contains(searchName)).toList();
        List<Entry<String, Game>> searchTagGames = games.entrySet().stream().filter(map -> map.getValue().getGameModesText().toLowerCase().contains(searchTags)).toList();
        List<Entry<String, Game>> searchFactionGames = games.entrySet().stream().filter(map -> map.getValue().getRealAndEliminatedPlayers().stream().map(p -> p.getFaction().toLowerCase()).anyMatch(s -> s.contains(searchFactions))).toList();
        List<Entry<String, Game>> searchUserGames = games.entrySet().stream().filter(map -> map.getValue().hasUser(searchUser)).toList();
        
        
        List<Entry<String, Game>> filteredListOfMaps = new ArrayList<>();
        if (includeNormalGames) filteredListOfMaps.addAll(normalGames);
        if (includeTIGLGames) filteredListOfMaps.addAll(tIGLGames);
        if (includeCommunityGames) filteredListOfMaps.addAll(communityGames);
        if (includeAllianceGames) filteredListOfMaps.addAll(allianceGames);
        if (includeFoWGames) filteredListOfMaps.addAll(foWGames);
        if (includeAbsolGames) filteredListOfMaps.addAll(absolGames);
        if (includeMiltyModGames) filteredListOfMaps.addAll(miltyModGames);
        if (includeDSGames) filteredListOfMaps.addAll(dSGames);
        if (includeFrankenGames) filteredListOfMaps.addAll(frankenGames);
        if (!searchName.isEmpty()) filteredListOfMaps.addAll(searchNameGames);
        if (!searchTags.isEmpty()) filteredListOfMaps.addAll(searchTagGames);
        if (!searchFactions.isEmpty()) filteredListOfMaps.addAll(searchFactionGames);
        if (searchUser != null) filteredListOfMaps.addAll(searchUserGames);
        if (!includeEndedGames) filteredListOfMaps.removeIf(g -> g.getValue().isHasEnded());

        Set<Entry<String, Game>> filteredGames = new HashSet<>(filteredListOfMaps);

        int totalGames = games.size();

        StringBuilder sb = new StringBuilder("__**Search Games:**__\n");
        sb.append("-# Statistics:\n");
        sb.append("-# > Total Games: `").append(totalGames).append("`\n");
        sb.append("-# > Games Found: `").append(filteredGames.size()).append("`\n");
        sb.append("-# > ").append(trueFalseEmoji(includeNormalGames)).append("includeNormalGames (").append(normalGames.size()).append("/").append(totalGames).append(")\n");
        sb.append("-# > ").append(trueFalseEmoji(includeTIGLGames)).append("includeTIGLGames (").append(tIGLGames.size()).append("/").append(totalGames).append(")\n");
        sb.append("-# > ").append(trueFalseEmoji(includeCommunityGames)).append("includeCommunityGames (").append(communityGames.size()).append("/").append(totalGames).append(")\n");
        sb.append("-# > ").append(trueFalseEmoji(includeAllianceGames)).append("includeAllianceGames (").append(allianceGames.size()).append("/").append(totalGames).append(")\n");
        sb.append("-# > ").append(trueFalseEmoji(includeFoWGames)).append("includeFoWGames (").append(foWGames.size()).append("/").append(totalGames).append(")\n");
        sb.append("-# > ").append(trueFalseEmoji(includeAbsolGames)).append("includeAbsolGames (").append(absolGames.size()).append("/").append(totalGames).append(")\n");
        sb.append("-# > ").append(trueFalseEmoji(includeMiltyModGames)).append("includeMiltyModGames (").append(miltyModGames.size()).append("/").append(totalGames).append(")\n");
        sb.append("-# > ").append(trueFalseEmoji(includeDSGames)).append("includeDSGames (").append(dSGames.size()).append("/").append(totalGames).append(")\n");
        sb.append("-# > ").append(trueFalseEmoji(includeFrankenGames)).append("includeFrankenGames (").append(frankenGames.size()).append("/").append(totalGames).append(")\n");
        sb.append("-# > ").append(trueFalseEmoji(includeEndedGames)).append("includeEndedGames (").append(endedGames.size()).append("/").append(totalGames).append(")\n");

        sb.append("-# > ").append(trueFalseEmoji(!searchName.isEmpty())).append("gamesWithName `").append(searchName).append("` (").append(searchNameGames.size()).append("/").append(totalGames).append(")\n");
        sb.append("-# > ").append(trueFalseEmoji(!searchTags.isEmpty())).append("gamesWithTag `").append(searchTags).append("` (").append(searchTagGames.size()).append("/").append(totalGames).append(")\n");
        sb.append("-# > ").append(trueFalseEmoji(!searchFactions.isEmpty())).append("gamesWithFaction `").append(searchFactions).append("` (").append(searchFactionGames.size()).append("/").append(totalGames).append(")\n");
        sb.append("-# > ").append(trueFalseEmoji(searchUser != null)).append("gamesWithUser `").append(searchUser != null ? searchUser.getEffectiveName() : "").append("` (").append(searchUserGames.size()).append("/").append(totalGames).append(")\n");

        if (filteredGames.isEmpty()) {
            sb.append("No games match the selected filters.");
        } else {
            sb.append(filteredGames.stream()
                .filter(map -> includeEndedGames || !map.getValue().isHasEnded())
                .sorted(Map.Entry.comparingByKey())
                .map(map -> getRepresentationText(games, map.getKey()))
                .collect(Collectors.joining("\n")));
        }

        String message = sb.toString();
        message = message.replace("``", "");
        MessageHelper.sendMessageToThread(event.getChannel(), "Search Games", message);
    }

    private String trueFalseEmoji(boolean bool) {
        return bool ? "✅" : "❌";
    }

    private String getRepresentationText(Map<String, Game> games, String mapName) {
        Game game = games.get(mapName);
        StringBuilder sb = new StringBuilder("- **" + mapName + "**").append(" ");
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
        return sb.toString();
    }
}
