package ti4.commands.help;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.swing.text.html.HTMLDocument.HTMLReader.IsindexAction;

public class ListGames extends HelpSubcommandData {

    public ListGames() {
        super(Constants.LIST_GAMES, "List all games");
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
        HashMap<String, Map> mapList = MapManager.getInstance().getMapList();
        boolean includeNormalGames = event.getOption(Constants.NORMAL_GAME, false, OptionMapping::getAsBoolean);
        boolean includeTIGLGames = event.getOption(Constants.TIGL_GAME, false, OptionMapping::getAsBoolean);
        boolean includeCommunityGames = event.getOption(Constants.COMMUNITY_MODE, false, OptionMapping::getAsBoolean);
        boolean includeAllianceGames = event.getOption(Constants.ALLIANCE_MODE, false, OptionMapping::getAsBoolean);
        boolean includeFoWGames = event.getOption(Constants.FOW_MODE, false, OptionMapping::getAsBoolean);
        boolean includeAbsolGames = event.getOption(Constants.ABSOL_MODE, false, OptionMapping::getAsBoolean);
        boolean includeDSGames = event.getOption(Constants.DISCORDANT_STARS_MODE, false, OptionMapping::getAsBoolean);
        boolean includeEndedGames = event.getOption(Constants.ENDED_GAMES, false, OptionMapping::getAsBoolean);



        StringBuilder sb = new StringBuilder("__**Map List:**__\n");
        List<Entry<String, Map>> filteredListOfMaps = new ArrayList<>();
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeNormalGames && map.getValue().isNormalGame()).toList());
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeTIGLGames && map.getValue().isCompetitiveTIGLGame()).toList());
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeCommunityGames && map.getValue().isCommunityMode()).toList());
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeAllianceGames && map.getValue().isAllianceMode()).toList());
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeFoWGames && map.getValue().isFoWMode()).toList());
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeAbsolGames && map.getValue().isAbsolMode()).toList());
        filteredListOfMaps.addAll(mapList.entrySet().stream().filter(map -> includeDSGames && map.getValue().isDiscordantStarsMode()).toList());

        Set<Entry<String, Map>> filteredSetOfMaps = new HashSet<>(filteredListOfMaps);

        if (filteredSetOfMaps.isEmpty()) {
            sb.append("> No maps match the selected filters.");
        } else {
            sb.append(filteredSetOfMaps.stream()
                .filter(map -> includeEndedGames ? true : !map.getValue().isHasEnded())
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(map -> getRepresentationText(mapList, map.getKey()))
                .collect(Collectors.joining("\n")));
        }

        MessageHelper.sendMessageToThread(event.getChannel(), "Map List", sb.toString());
    }

    private String getRepresentationText(HashMap<String, Map> mapList, String mapName) {
        Map mapToShow = mapList.get(mapName);
        StringBuilder representationText = new StringBuilder("> **" + mapName + "**").append(" ");
        representationText.append("   Created: ").append(mapToShow.getCreationDate());
        representationText.append("   Last Modified: ").append(Helper.getDateRepresentation(mapToShow.getLastModifiedDate())).append("  ");
        for (Player player : mapToShow.getPlayers().values()) {
            if (!mapToShow.isFoWMode() && player.getFaction() != null) {
                representationText.append(Helper.getFactionIconFromDiscord(player.getFaction()));
            }
        }
        representationText.append(" [" + mapToShow.getGameModesText()).append("] ");
        if (mapToShow.isHasEnded()) representationText.append(" ENDED");
        return representationText.toString();
    }
}
