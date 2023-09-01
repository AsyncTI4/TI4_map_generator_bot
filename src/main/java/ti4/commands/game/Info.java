package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;


public class Info extends GameSubcommandData {
    public static final String NEW_LINE = "\n";

    public Info() {
        super(Constants.INFO, "Game information:");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game Name"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MapManager mapManager = MapManager.getInstance();
        Map activeMap = getActiveMap();
        if (activeMap == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Game not found.");
            return;
        }

        OptionMapping gameNameOption = event.getOption(Constants.GAME_NAME);
        if (gameNameOption != null && (activeMap == null || !activeMap.getName().equalsIgnoreCase(gameNameOption.getAsString().toLowerCase()))) {
            activeMap = mapManager.getMap(gameNameOption.getAsString().toLowerCase());
        }

        StringBuilder sb = getGameInfo(activeMap, event);
        MessageHelper.replyToMessage(event, sb.toString());
    }

    public static StringBuilder getGameInfo(Map activeMap, SlashCommandInteractionEvent event) {
        Boolean privateGame = FoWHelper.isPrivateGame(activeMap, event);

        StringBuilder sb = new StringBuilder();
        sb.append("## Game Info:").append(NEW_LINE);
        sb.append("### Name: " + activeMap.getName()).append(NEW_LINE);
        sb.append("Owner: " + activeMap.getOwnerName()).append(NEW_LINE);
        sb.append("Created: " + activeMap.getCreationDate()).append(NEW_LINE);
        sb.append("Last Modified: " + Helper.getDateRepresentation(activeMap.getLastModifiedDate())).append(NEW_LINE);
        sb.append("Status: `" + activeMap.getMapStatus()).append("`").append(NEW_LINE);
        sb.append("Ended: `" + activeMap.isHasEnded()).append("`").append(NEW_LINE);
        if (activeMap.isHasEnded()) sb.append("> Date Ended: " + Helper.getDateRepresentation(activeMap.getEndedDate())).append(NEW_LINE);
        
        sb.append("### Setup: ").append(NEW_LINE);
        sb.append("VP Count: " + activeMap.getVp()).append(NEW_LINE);
        sb.append("Private Game: " + privateGame).append(NEW_LINE);
        sb.append("Game Modes: " + activeMap.getGameModesText()).append(NEW_LINE);
        if (!privateGame) {
            sb.append("Map String: `" + Helper.getMapString(activeMap)).append("`").append(NEW_LINE);
        } else {
            sb.append("Map String: Cannot show map string for private games").append(NEW_LINE);
        }
        sb.append("Strategy Card Set: `").append(activeMap.getScSetID()).append("`").append(NEW_LINE);
        sb.append("Decks: ").append(NEW_LINE);
        sb.append("- ").append(Emojis.ActionCard).append("Action Card Deck: `").append(activeMap.getAcDeckID()).append("` ")
            .append(activeMap.getActionCardDeckSize()).append("/").append(activeMap.getActionCardFullDeckSize()).append(NEW_LINE);
        sb.append("- ").append(Emojis.SecretObjective).append("Secret Objective Deck: `").append(activeMap.getSoDeckID()).append("` ").append(activeMap.getSecretObjectiveDeckSize()).append("/").append(activeMap.getSecretObjectiveFullDeckSize()).append(NEW_LINE);
        sb.append("- ").append(Emojis.Public1).append("Stage 1 Public Objective Deck: `").append(activeMap.getStage1PublicDeckID()).append("` ").append(activeMap.getPublicObjectives1DeckSize()).append("/").append(activeMap.getPublicObjectives1FullDeckSize()).append(NEW_LINE);
        sb.append("- ").append(Emojis.Public2).append("Stage 2 Public Objective Deck: `").append(activeMap.getStage2PublicDeckID()).append("` ").append(activeMap.getPublicObjectives2DeckSize()).append("/").append(activeMap.getPublicObjectives2FullDeckSize()).append(NEW_LINE);
        sb.append("- ").append(Emojis.Agenda).append("Agenda Deck: `").append(activeMap.getAgendaDeckID()).append("` ").append(activeMap.getAgendaDeckSize()).append("/").append(activeMap.getAgendaFullDeckSize()).append(NEW_LINE);
        sb.append("- ").append(Emojis.RelicCard).append("Relic Deck: `").append(activeMap.getRelicDeckID()).append("` ").append(activeMap.getRelicDeckSize()).append("/").append(activeMap.getRelicFullDeckSize()).append(NEW_LINE);
        sb.append("- Exploration Deck: `").append(activeMap.getExplorationDeckID()).append("`").append(NEW_LINE);
        sb.append(" - ").append(Emojis.IndustrialCard).append("Industrial Deck: ").append(activeMap.getIndustrialExploreDeckSize()).append("/").append(activeMap.getIndustrialExploreFullDeckSize()).append(NEW_LINE);
        sb.append(" - ").append(Emojis.HazardousCard).append("Hazardous Deck: ").append(activeMap.getHazardousExploreDeckSize()).append("/").append(activeMap.getHazardousExploreFullDeckSize()).append(NEW_LINE);
        sb.append(" - ").append(Emojis.CulturalCard).append("Cultural Deck: ").append(activeMap.getCulturalExploreDeckSize()).append("/").append(activeMap.getCulturalExploreFullDeckSize()).append(NEW_LINE);
        sb.append(" - ").append(Emojis.FrontierCard).append("Frontier Deck: ").append(activeMap.getFrontierExploreDeckSize()).append("/").append(activeMap.getFrontierExploreFullDeckSize()).append(NEW_LINE);
        
        sb.append("### Settings: ").append(NEW_LINE);
        sb.append("Auto-Ping Time Interval (hrs): " + activeMap.getAutoPingSpacer()).append(NEW_LINE);
        sb.append("Text Size: " + activeMap.getLargeText()).append(NEW_LINE);
        sb.append("Output Verbosity: " + activeMap.getOutputVerbosity()).append(NEW_LINE);
        sb.append("CC/Plastic warnings: " + activeMap.getCCNPlasticLimit()).append(NEW_LINE);
        if (!privateGame) {
            sb.append("### Players: ").append(NEW_LINE);
            int index = 1;
            for (Player player : activeMap.getRealPlayers()) {
                sb.append("> `").append(index).append(".` ").append(Helper.getFactionIconFromDiscord(player.getFaction())).append(player.getUserName()).append(Helper.getColourAsMention(event.getGuild(), player.getColor()));
                if (player.getRoleIDForCommunity() != null) sb.append(" - Community Role: ").append(player.getRoleForCommunity().getName());
                sb.append(NEW_LINE);
                index++;
            }

            sb.append("### Other Players: ").append(NEW_LINE);
            for (Player player : activeMap.getNotRealPlayers()) {
                sb.append("> `").append(index).append(".` ").append(player.getUserName());
                sb.append(NEW_LINE);
                index++;
            }
        } else {
            sb.append("### Players: Cannot show players for private games").append(NEW_LINE);
        }
        
        sb.append("### Other Stats: ").append(NEW_LINE);
        sb.append("Current Phase: " + activeMap.getCurrentPhase()).append(NEW_LINE);
        sb.append("Ring Count: " +activeMap.getRingCount()).append(NEW_LINE);
        sb.append("Game Player Count: " + activeMap.getPlayerCountForMap()).append(NEW_LINE);
        sb.append("Game Real Player Count: " + activeMap.getRealPlayers().size()).append(NEW_LINE);
        sb.append("Map Images Generated: " + activeMap.getMapImageGenerationCount()).append(NEW_LINE);
        sb.append("SC Trade Goods: `").append(activeMap.getScTradeGoods()).append("`").append(NEW_LINE);
        sb.append("Public Objectives: `").append(activeMap.getRevealedPublicObjectives()).append("`").append(NEW_LINE);
        sb.append("Laws: `").append(activeMap.getLaws()).append("`").append(NEW_LINE);
        sb.append("Laws Info: `").append(activeMap.getLawsInfo()).append("`").append(NEW_LINE);
        sb.append("Migrations Run: `").append(activeMap.getRunMigrations()).append("`").append(NEW_LINE);

        return sb;
    }
}
