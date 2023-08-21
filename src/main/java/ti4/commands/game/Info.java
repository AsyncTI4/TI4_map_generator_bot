package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class Info extends GameSubcommandData{
    public static final String NEW_LINE = "\n";

    public Info() {
        super(Constants.INFO, "Game information:");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game Name"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping gameOption = event.getOption(Constants.GAME_NAME);
        MapManager mapManager = MapManager.getInstance();
        Map activeMap = mapManager.getUserActiveMap(event.getUser().getId());
        StringBuilder sb = getGameInfo(gameOption, mapManager, activeMap, event);
        MessageHelper.replyToMessage(event, sb.toString());
    }

    public static StringBuilder getGameInfo(OptionMapping gameOption, MapManager mapManager, Map activeMap, SlashCommandInteractionEvent event) {
        StringBuilder sb = new StringBuilder();
        Boolean privateGame = FoWHelper.isPrivateGame(activeMap, event);
        if (activeMap == null && gameOption == null){
            sb.append("Game not specified");
            return sb;
        } else if (activeMap == null ){
            activeMap = mapManager.getMap(gameOption.getAsString());
        }
        sb.append("Game Info:").append(NEW_LINE);
        sb.append("Game name: " + activeMap.getName()).append(NEW_LINE);
        sb.append("Game owner: " + activeMap.getOwnerName()).append(NEW_LINE);
        sb.append("Game status: " + activeMap.getMapStatus());
        if (activeMap.isHasEnded()) sb.append(" - GAME HAS ENDED");
        sb.append(NEW_LINE);
        sb.append("Game Modes: " + activeMap.getGameModesText()).append(NEW_LINE);
        sb.append("Strategy Card Set: `").append(activeMap.getScSetID()).append("`").append(NEW_LINE);
        sb.append("Decks: ").append(NEW_LINE);
        sb.append("> Action Card Deck: `").append(activeMap.getAcDeckID()).append("`").append(NEW_LINE);
        sb.append("> Secret Objective Deck: `").append(activeMap.getSoDeckID()).append("`").append(NEW_LINE);
        sb.append("> Stage 1 Public Objective Deck: `").append(activeMap.getStage1PublicDeckID()).append("`").append(NEW_LINE);
        sb.append("> Stage 2 Public Objective Deck: `").append(activeMap.getStage2PublicDeckID()).append("`").append(NEW_LINE);
        sb.append("> Relic Deck: `").append(activeMap.getRelicDeckID()).append("`").append(NEW_LINE);
        sb.append("> Agenda Deck: `").append(activeMap.getAgendaDeckID()).append("`").append(NEW_LINE);
        sb.append("> Exploration Deck: `").append(activeMap.getExplorationDeckID()).append("`").append(NEW_LINE);
        sb.append("Current Phase: " +activeMap.getCurrentPhase()).append(NEW_LINE);
        sb.append("Ring Count: " +activeMap.getRingCount()).append(NEW_LINE);
        sb.append("Auto-Ping Time Interval (hrs): " + activeMap.getAutoPingSpacer()).append(NEW_LINE);
        sb.append("Created: " + activeMap.getCreationDate()).append(NEW_LINE);
        sb.append("Last Modified: " + Helper.getDateRepresentation(activeMap.getLastModifiedDate())).append(NEW_LINE);
        sb.append("Map Images Generated: " + activeMap.getMapImageGenerationCount()).append(NEW_LINE);
        if (privateGame == null || privateGame == false) {
            sb.append("Map String: `" + Helper.getMapString(activeMap)).append("`").append(NEW_LINE);
        } else {
            sb.append("Map String: Cannot show map string for private games").append(NEW_LINE);
        }
        sb.append("Game player count: " + activeMap.getPlayerCountForMap()).append(NEW_LINE);
        if (privateGame == null || privateGame == false) {
            sb.append("Players: ").append(NEW_LINE);
            HashMap<String, Player> mapPlayers = activeMap.getPlayers();
            int index = 1;
            ArrayList<Player> players = new ArrayList<>(mapPlayers.values());
            for (Player player : players) {
                if (player.getFaction() == null) continue;
                
                sb.append("> `").append(index).append(".` ").append(player.getUserName()).append(Helper.getFactionIconFromDiscord(player.getFaction())).append(Helper.getColourAsMention(event.getGuild(), player.getColor()));
                sb.append(" - *").append(player.getTotalVictoryPoints(activeMap)).append("VP* ");
                sb.append(NEW_LINE);
                index++;
            }
        } else {
            sb.append("Players: Cannot show players for private games").append(NEW_LINE);
        }
        sb.append("Public Objectives:\n> `").append(activeMap.getRevealedPublicObjectives()).append("`").append(NEW_LINE);
        sb.append("Laws:\n> `").append(activeMap.getLaws()).append("`").append(NEW_LINE);

        return sb;
    }
}
