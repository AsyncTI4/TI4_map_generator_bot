package ti4.commands2.game;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.settings.users.UserSettingsManager;

class Info extends GameStateSubcommand {

    public Info() {
        super(Constants.INFO, "Game information:", false, false);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game Name").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        StringBuilder sb = getGameInfo(getGame(), event);
        MessageHelper.replyToMessage(event, sb.toString());
    }

    public static StringBuilder getGameInfo(Game game, SlashCommandInteractionEvent event) {
        boolean privateGame = FoWHelper.isPrivateGame(game, event);

        StringBuilder sb = new StringBuilder();
        sb.append("## Game Info:").append("\n");
        sb.append("### Name: ").append(game.getName()).append("\n");
        sb.append("Owner: ").append(game.getOwnerName()).append("\n");
        sb.append("Created: ").append(game.getCreationDate()).append("\n");
        sb.append("Last Modified: ").append(Helper.getDateRepresentation(game.getLastModifiedDate())).append("\n");
        sb.append("Ended: `").append(game.isHasEnded()).append("`").append("\n");
        if (game.isHasEnded()) sb.append("> Date Ended: ").append(Helper.getDateRepresentation(game.getEndedDate())).append("\n");
        sb.append("Game Completed: `").append(game.getWinner().isPresent()).append("`").append("\n");

        sb.append("### Setup: ").append("\n");
        sb.append("VP Count: ").append(game.getVp()).append("\n");
        sb.append("SO Count: ").append(game.getMaxSOCountPerPlayer()).append("\n");
        sb.append("Private Game: ").append(privateGame).append("\n");
        sb.append("Game Modes: ").append(game.getGameModesText()).append("\n");
        if (game.isCompetitiveTIGLGame()) {
            // Game Rank
            sb.append("TIGL Rank of Game: **");
            TIGLRank rank = game.getMinimumTIGLRankAtGameStart();
            if (rank == null) {
                sb.append(" Unknown Rank");
            } else {
                sb.append(game.getMinimumTIGLRankAtGameStart().getName()).append("**");
            }
            sb.append("\n");

            // Player Rank
            sb.append("TIGL Ranks at Start of Game:\n");
            for (Player player : game.getPlayers().values()) {
                rank = player.getPlayerTIGLRankAtGameStart();
                sb.append("> ").append(player.getRepresentationNoPing());
                if (rank == null) {
                    sb.append(" Unknown Rank");
                } else {
                    sb.append(" **").append(rank.getName()).append("**");
                }
                sb.append("\n");
            }
        }
        sb.append("Map Template: `").append(game.getMapTemplateID()).append("`").append("\n");
        if (!privateGame || game.isHasEnded()) {
            sb.append("Map String: `").append(game.getMapString()).append("`").append("\n");
            sb.append("Hex Summary: `").append(game.getHexSummary()).append("`").append("\n");
        } else {
            sb.append("Map String: Cannot show map string for private games").append("\n");
        }
        sb.append("Strategy Card Set: `").append(game.getScSetID()).append("`").append("\n");
        sb.append("Strategy Cards: `").append(game.getStrategyCardSet().getScIDs()).append("`").append("\n");
        sb.append("Decks: ").append("\n");
        sb.append("- ").append(Emojis.ActionCard).append("Action Card Deck: `").append(game.getAcDeckID()).append("` ").append(game.getActionCardDeckSize()).append("/").append(game.getActionCardFullDeckSize()).append("\n");
        sb.append("- ").append(Emojis.SecretObjective).append("Secret Objective Deck: `").append(game.getSoDeckID()).append("` ").append(game.getSecretObjectiveDeckSize()).append("/").append(game.getSecretObjectiveFullDeckSize()).append("\n");
        sb.append("- ").append(Emojis.Public1).append("Stage 1 Public Objective Deck: `").append(game.getStage1PublicDeckID()).append("` ").append(game.getPublicObjectives1DeckSize()).append("/").append(game.getPublicObjectives1FullDeckSize());
        sb.append(" (+").append(game.getPublicObjectives1Peakable().size()).append(" are staged/peekable)\n");
        sb.append("- ").append(Emojis.Public2).append("Stage 2 Public Objective Deck: `").append(game.getStage2PublicDeckID()).append("` ").append(game.getPublicObjectives2DeckSize()).append("/").append(game.getPublicObjectives2FullDeckSize());
        sb.append(" (+").append(game.getPublicObjectives2Peakable().size()).append(" are staged/peekable)\n");
        sb.append("- ").append(Emojis.Agenda).append("Agenda Deck: `").append(game.getAgendaDeckID()).append("` ").append(game.getAgendaDeckSize()).append("/").append(game.getAgendaFullDeckSize()).append("\n");
        if (game.getEventDeckID() != null && !"null".equals(game.getEventDeckID()) && !game.getEventDeckID().isEmpty()) {
            sb.append("- ").append("Event Deck: `").append(game.getEventDeckID()).append("` ").append(game.getEventDeckSize()).append("/").append(game.getEventFullDeckSize()).append("\n");
        }
        sb.append("- ").append(Emojis.NonUnitTechSkip).append("Technology Deck: `").append(game.getTechnologyDeckID()).append("`").append("\n");
        sb.append("- ").append(Emojis.RelicCard).append("Relic Deck: `").append(game.getRelicDeckID()).append("` ").append(game.getRelicDeckSize()).append("/").append(game.getRelicFullDeckSize()).append("\n");
        sb.append("- Exploration Deck: `").append(game.getExplorationDeckID()).append("`").append("\n");
        sb.append(" - ").append(Emojis.IndustrialCard).append("Industrial Deck: ").append(game.getIndustrialExploreDeckSize()).append("/").append(game.getIndustrialExploreFullDeckSize()).append("\n");
        sb.append(" - ").append(Emojis.HazardousCard).append("Hazardous Deck: ").append(game.getHazardousExploreDeckSize()).append("/").append(game.getHazardousExploreFullDeckSize()).append("\n");
        sb.append(" - ").append(Emojis.CulturalCard).append("Cultural Deck: ").append(game.getCulturalExploreDeckSize()).append("/").append(game.getCulturalExploreFullDeckSize()).append("\n");
        sb.append(" - ").append(Emojis.FrontierCard).append("Frontier Deck: ").append(game.getFrontierExploreDeckSize()).append("/").append(game.getFrontierExploreFullDeckSize()).append("\n");

        sb.append("### Settings: ").append("\n");
        sb.append("Beta Test Mode: ").append(game.isTestBetaFeaturesMode()).append("\n");
        sb.append("Game Auto-Ping Time Interval (hrs): ").append(game.getAutoPingSpacer()).append("\n");
        sb.append("Player's Auto-Ping Time Interval (hrs):\n");
        for (Player player : game.getRealPlayers()) {
            String interval = String.valueOf(UserSettingsManager.get(player.getUserID()).getPersonalPingInterval());
            if ("0".equals(interval)) {
                interval = "Off";
            }
            sb.append("> ").append(player.getFactionEmojiOrColor()).append(": `").append(interval).append("`\n");
        }
        sb.append("Text Size: ").append(game.getTextSize()).append("\n");
        sb.append("Full Text Output: ").append(game.isShowFullComponentTextEmbeds()).append("\n");
        sb.append("Output Verbosity: ").append(game.getOutputVerbosity()).append("\n");
        if (game.getTableTalkChannel() != null) sb.append("Table Talk Channel: ").append(game.getTableTalkChannel().getAsMention()).append("\n");
        if (game.getActionsChannel() != null) sb.append("Actions Channel: ").append(game.getActionsChannel().getAsMention()).append("\n");
        if (game.getBotMapUpdatesThread() != null) sb.append("Bot Map Thread: ").append(game.getBotMapUpdatesThread().getAsMention()).append("\n");
        if (game.getLaunchPostThread() != null) sb.append("Launch Post Thread: ").append(game.getLaunchPostThread().getAsMention()).append("\n");
        if (game.isFowMode()) {
            sb.append("FoW Options:");
            for (Map.Entry<String, String> entry : game.getFowOptions().entrySet()) {
                sb.append(" ").append(entry.getKey()).append(":").append(entry.getValue());
            }
            sb.append("\n");
        }

        if (!privateGame) {
            sb.append("### Players: ").append("\n");
            int index = 1;
            for (Player player : game.getRealPlayers()) {
                sb.append("> `").append(index).append(".` ").append(player.getFactionEmoji()).append(player.getUserName()).append(Emojis.getColorEmojiWithName(player.getColor()));
                if (player.getRoleForCommunity() != null) sb.append(" - Community Role: ").append(player.getRoleForCommunity().getName());
                sb.append("\n");
                index++;
            }

            sb.append("### Other Players: ").append("\n");
            for (Player player : game.getNotRealPlayers()) {
                sb.append("> `").append(index).append(".` ").append(player.getUserName());
                sb.append("\n");
                index++;
            }
        } else {
            sb.append("### Players: Cannot show players for private games").append("\n");
        }

        sb.append("### Other Stats: ").append("\n");
        sb.append("Current Phase: ").append(game.getPhaseOfGame()).append("\n");
        sb.append("Game Player Count: ").append(game.getPlayerCountForMap()).append("\n");
        sb.append("Game Real Player Count: ").append(game.getRealPlayers().size()).append("\n");
        sb.append("GMIDs: `").append(game.getFogOfWarGMIDs()).append("`\n");
        sb.append("SCs per player: ").append(game.getStrategyCardsPerPlayer()).append("\n");
        sb.append("Map Images Generated: ").append(game.getMapImageGenerationCount()).append("\n");
        sb.append("SC Trade Goods: `").append(game.getScTradeGoods()).append("`").append("\n");
        sb.append("Public Objectives: `").append(game.getRevealedPublicObjectives()).append("`").append("\n");
        sb.append("Laws: `").append(game.getLaws()).append("`").append("\n");
        sb.append("Laws Info: `").append(game.getLawsInfo()).append("`").append("\n");
        sb.append("Events: `").append(game.getEventsInEffect()).append("`").append("\n");
        sb.append("Migrations Run: `").append(game.getRunMigrations()).append("`").append("\n");
        sb.append("Buttons pressed: `").append(game.getButtonPressCount()).append("`").append("\n");
        sb.append("SlashCommands used: `").append(game.getSlashCommandsRunCount()).append("`").append("\n");

        return sb;
    }
}
