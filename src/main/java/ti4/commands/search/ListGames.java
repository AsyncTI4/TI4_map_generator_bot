package ti4.commands.search;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class ListGames extends SearchSubcommandData {

    public ListGames() {
        super(Constants.SEARCH_GAMES, "List all games");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.NORMAL_GAME, "True to include Normal (none of the other modes) games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.TIGL_GAME, "True to include TIGL games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.COMMUNITY_MODE, "True to include community games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ALLIANCE_MODE, "True to include alliance games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FOW_MODE, "True to include Fog of War games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ABSOL_MODE, "True to include Absol's Agenda & Relics games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DISCORDANT_STARS_MODE, "True to include Discordant Stars games"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ENDED_GAMES, "True to also show ended games"));
    }


    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        boolean includeNormalGames = event.getOption(Constants.NORMAL_GAME, false, OptionMapping::getAsBoolean);
        boolean includeTIGLGames = event.getOption(Constants.TIGL_GAME, false, OptionMapping::getAsBoolean);
        boolean includeCommunityGames = event.getOption(Constants.COMMUNITY_MODE, false, OptionMapping::getAsBoolean);
        boolean includeAllianceGames = event.getOption(Constants.ALLIANCE_MODE, false, OptionMapping::getAsBoolean);
        boolean includeFoWGames = event.getOption(Constants.FOW_MODE, false, OptionMapping::getAsBoolean);
        boolean includeAbsolGames = event.getOption(Constants.ABSOL_MODE, false, OptionMapping::getAsBoolean);
        boolean includeDSGames = event.getOption(Constants.DISCORDANT_STARS_MODE, false, OptionMapping::getAsBoolean);
        boolean includeEndedGames = event.getOption(Constants.ENDED_GAMES, false, OptionMapping::getAsBoolean);



        StringBuilder sb = new StringBuilder("__**Map List:**__\n");
        List<Entry<String, Game>> filteredListOfMaps = new ArrayList<>();
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeNormalGames && map.getValue().isNormalGame()).toList());
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeTIGLGames && map.getValue().isCompetitiveTIGLGame()).toList());
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeCommunityGames && map.getValue().isCommunityMode()).toList());
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeAllianceGames && map.getValue().isAllianceMode()).toList());
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeFoWGames && map.getValue().isFoWMode()).toList());
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeAbsolGames && map.getValue().isAbsolMode()).toList());
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeDSGames && map.getValue().isDiscordantStarsMode()).toList());

        Set<Entry<String, Game>> filteredSetOfMaps = new HashSet<>(filteredListOfMaps);

        if (filteredSetOfMaps.isEmpty()) {
            sb.append("> No maps match the selected filters.");
        } else {
            sb.append(filteredSetOfMaps.stream()
                .filter(map -> includeEndedGames || !map.getValue().isHasEnded())
                .sorted(Map.Entry.comparingByKey())
                .map(map -> getRepresentationText(mapList, map.getKey()))
                .collect(Collectors.joining("\n")));
        }

        MessageHelper.sendMessageToThread(event.getChannel(), "Map List", sb.toString());
    }

    private String getRepresentationText(Map<String, Game> mapList, String mapName) {
        Game gameToShow = mapList.get(mapName);
        StringBuilder representationText = new StringBuilder("> **" + mapName + "**").append(" ");
        representationText.append("   Created: ").append(gameToShow.getCreationDate());
        representationText.append("   Last Modified: ").append(Helper.getDateRepresentation(gameToShow.getLastModifiedDate())).append("  ");
        for (Player player : gameToShow.getPlayers().values()) {
            if (!gameToShow.isFoWMode() && player.getFaction() != null) {
                representationText.append(player.getFactionEmoji());
            }
        }
        representationText.append(" [").append(gameToShow.getGameModesText()).append("] ");
        if (gameToShow.isHasEnded()) representationText.append(" ENDED");
        return representationText.toString();
    }
}
