package ti4.commands.game;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.user.UserSettingsManager;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class Info extends GameSubcommandData {
    public static final String NEW_LINE = "\n";

    public Info() {
        super(Constants.INFO, "Game information:");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game Name").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        if (game == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Game not found.");
            return;
        }

        OptionMapping gameNameOption = event.getOption(Constants.GAME_NAME);
        if (gameNameOption != null && !game.getName().equalsIgnoreCase(gameNameOption.getAsString().toLowerCase())) {
            game = GameManager.getGame(gameNameOption.getAsString().toLowerCase());
        }

        StringBuilder sb = getGameInfo(game, event);
        MessageHelper.replyToMessage(event, sb.toString());
    }

    public static StringBuilder getGameInfo(Game game, SlashCommandInteractionEvent event) {
        boolean privateGame = FoWHelper.isPrivateGame(game, event);

        StringBuilder sb = new StringBuilder();
        sb.append("## Game Info:").append(NEW_LINE);
        sb.append("### Name: ").append(game.getName()).append(NEW_LINE);
        sb.append("Owner: ").append(game.getOwnerName()).append(NEW_LINE);
        sb.append("Created: ").append(game.getCreationDate()).append(NEW_LINE);
        sb.append("Last Modified: ").append(Helper.getDateRepresentation(game.getLastModifiedDate())).append(NEW_LINE);
        sb.append("Ended: `").append(game.isHasEnded()).append("`").append(NEW_LINE);
        if (game.isHasEnded()) sb.append("> Date Ended: ").append(Helper.getDateRepresentation(game.getEndedDate())).append(NEW_LINE);
        sb.append("Game Completed: `").append(game.getWinner().isPresent()).append("`").append(NEW_LINE);

        sb.append("### Setup: ").append(NEW_LINE);
        sb.append("VP Count: ").append(game.getVp()).append(NEW_LINE);
        sb.append("SO Count: ").append(game.getMaxSOCountPerPlayer()).append(NEW_LINE);
        sb.append("Private Game: ").append(privateGame).append(NEW_LINE);
        sb.append("Game Modes: ").append(game.getGameModesText()).append(NEW_LINE);
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
        sb.append("Map Template: `").append(game.getMapTemplateID()).append("`").append(NEW_LINE);
        if (!privateGame || game.isHasEnded()) {
            sb.append("Map String: `").append(game.getMapString()).append("`").append(NEW_LINE);
        } else {
            sb.append("Map String: Cannot show map string for private games").append(NEW_LINE);
        }
        sb.append("Strategy Card Set: `").append(game.getScSetID()).append("`").append(NEW_LINE);
        sb.append("Strategy Cards: `").append(game.getStrategyCardSet().getScIDs()).append("`").append(NEW_LINE);
        sb.append("Decks: ").append(NEW_LINE);
        sb.append("- ").append(Emojis.ActionCard).append("Action Card Deck: `").append(game.getAcDeckID()).append("` ").append(game.getActionCardDeckSize()).append("/").append(game.getActionCardFullDeckSize()).append(NEW_LINE);
        sb.append("- ").append(Emojis.SecretObjective).append("Secret Objective Deck: `").append(game.getSoDeckID()).append("` ").append(game.getSecretObjectiveDeckSize()).append("/").append(game.getSecretObjectiveFullDeckSize()).append(NEW_LINE);
        sb.append("- ").append(Emojis.Public1).append("Stage 1 Public Objective Deck: `").append(game.getStage1PublicDeckID()).append("` ").append(game.getPublicObjectives1DeckSize()).append("/").append(game.getPublicObjectives1FullDeckSize());
        sb.append(" (+").append(game.getPublicObjectives1Peakable().size()).append(" are staged/peekable)\n");
        sb.append("- ").append(Emojis.Public2).append("Stage 2 Public Objective Deck: `").append(game.getStage2PublicDeckID()).append("` ").append(game.getPublicObjectives2DeckSize()).append("/").append(game.getPublicObjectives2FullDeckSize());
        sb.append(" (+").append(game.getPublicObjectives2Peakable().size()).append(" are staged/peekable)\n");
        sb.append("- ").append(Emojis.Agenda).append("Agenda Deck: `").append(game.getAgendaDeckID()).append("` ").append(game.getAgendaDeckSize()).append("/").append(game.getAgendaFullDeckSize()).append(NEW_LINE);
        if (game.getEventDeckID() != null && !"null".equals(game.getEventDeckID()) && !game.getEventDeckID().isEmpty()) {
            sb.append("- ").append("Event Deck: `").append(game.getEventDeckID()).append("` ").append(game.getEventDeckSize()).append("/").append(game.getEventFullDeckSize()).append(NEW_LINE);
        }
        sb.append("- ").append(Emojis.NonUnitTechSkip).append("Technology Deck: `").append(game.getTechnologyDeckID()).append("`").append(NEW_LINE);
        sb.append("- ").append(Emojis.RelicCard).append("Relic Deck: `").append(game.getRelicDeckID()).append("` ").append(game.getRelicDeckSize()).append("/").append(game.getRelicFullDeckSize()).append(NEW_LINE);
        sb.append("- Exploration Deck: `").append(game.getExplorationDeckID()).append("`").append(NEW_LINE);
        sb.append(" - ").append(Emojis.IndustrialCard).append("Industrial Deck: ").append(game.getIndustrialExploreDeckSize()).append("/").append(game.getIndustrialExploreFullDeckSize()).append(NEW_LINE);
        sb.append(" - ").append(Emojis.HazardousCard).append("Hazardous Deck: ").append(game.getHazardousExploreDeckSize()).append("/").append(game.getHazardousExploreFullDeckSize()).append(NEW_LINE);
        sb.append(" - ").append(Emojis.CulturalCard).append("Cultural Deck: ").append(game.getCulturalExploreDeckSize()).append("/").append(game.getCulturalExploreFullDeckSize()).append(NEW_LINE);
        sb.append(" - ").append(Emojis.FrontierCard).append("Frontier Deck: ").append(game.getFrontierExploreDeckSize()).append("/").append(game.getFrontierExploreFullDeckSize()).append(NEW_LINE);

        sb.append("### Settings: ").append(NEW_LINE);
        sb.append("Beta Test Mode: ").append(game.isTestBetaFeaturesMode()).append(NEW_LINE);
        sb.append("Game Auto-Ping Time Interval (hrs): ").append(game.getAutoPingSpacer()).append(NEW_LINE);
        sb.append("Player's Auto-Ping Time Interval (hrs):\n");
        for (Player player : game.getRealPlayers()) {
            String interval = String.valueOf(UserSettingsManager.get(player.getUserID()));
            if ("0".equals(interval)) {
                interval = "Off";
            }
            sb.append("> ").append(player.getFactionEmojiOrColor()).append(": `").append(interval).append("`\n");
        }
        sb.append("Text Size: ").append(game.getTextSize()).append(NEW_LINE);
        sb.append("Full Text Output: ").append(game.isShowFullComponentTextEmbeds()).append(NEW_LINE);
        sb.append("Output Verbosity: ").append(game.getOutputVerbosity()).append(NEW_LINE);
        if (game.getTableTalkChannel() != null) sb.append("Table Talk Channel: ").append(game.getTableTalkChannel().getAsMention()).append(NEW_LINE);
        if (game.getActionsChannel() != null) sb.append("Actions Channel: ").append(game.getActionsChannel().getAsMention()).append(NEW_LINE);
        if (game.getBotMapUpdatesThread() != null) sb.append("Bot Map Thread: ").append(game.getBotMapUpdatesThread().getAsMention()).append(NEW_LINE);
        if (game.getLaunchPostThread() != null) sb.append("Launch Post Thread: ").append(game.getLaunchPostThread().getAsMention()).append(NEW_LINE);
        if (game.isFowMode()) {
            sb.append("FoW Options:");
            for (Map.Entry<String, String> entry : game.getFowOptions().entrySet()) {
                sb.append(" ").append(entry.getKey()).append(":").append(entry.getValue());
            }
            sb.append(NEW_LINE);
        }

        if (!privateGame) {
            sb.append("### Players: ").append(NEW_LINE);
            int index = 1;
            for (Player player : game.getRealPlayers()) {
                sb.append("> `").append(index).append(".` ").append(player.getFactionEmoji()).append(player.getUserName()).append(Emojis.getColorEmojiWithName(player.getColor()));
                if (player.getRoleForCommunity() != null) sb.append(" - Community Role: ").append(player.getRoleForCommunity().getName());
                sb.append(NEW_LINE);
                index++;
            }

            sb.append("### Other Players: ").append(NEW_LINE);
            for (Player player : game.getNotRealPlayers()) {
                sb.append("> `").append(index).append(".` ").append(player.getUserName());
                sb.append(NEW_LINE);
                index++;
            }
        } else {
            sb.append("### Players: Cannot show players for private games").append(NEW_LINE);
        }

        sb.append("### Other Stats: ").append(NEW_LINE);
        sb.append("Current Phase: ").append(game.getPhaseOfGame()).append(NEW_LINE);
        sb.append("Game Player Count: ").append(game.getPlayerCountForMap()).append(NEW_LINE);
        sb.append("Game Real Player Count: ").append(game.getRealPlayers().size()).append(NEW_LINE);
        sb.append("GMIDs: `").append(game.getFogOfWarGMIDs()).append("`\n");
        sb.append("SCs per player: ").append(game.getStrategyCardsPerPlayer()).append(NEW_LINE);
        sb.append("Map Images Generated: ").append(game.getMapImageGenerationCount()).append(NEW_LINE);
        sb.append("SC Trade Goods: `").append(game.getScTradeGoods()).append("`").append(NEW_LINE);
        sb.append("Public Objectives: `").append(game.getRevealedPublicObjectives()).append("`").append(NEW_LINE);
        sb.append("Laws: `").append(game.getLaws()).append("`").append(NEW_LINE);
        sb.append("Laws Info: `").append(game.getLawsInfo()).append("`").append(NEW_LINE);
        sb.append("Events: `").append(game.getEventsInEffect()).append("`").append(NEW_LINE);
        sb.append("Migrations Run: `").append(game.getRunMigrations()).append("`").append(NEW_LINE);
        sb.append("Buttons pressed: `").append(game.getButtonPressCount()).append("`").append(NEW_LINE);
        sb.append("SlashCommands used: `").append(game.getSlashCommandsRunCount()).append("`").append(NEW_LINE);

        return sb;
    }
}
