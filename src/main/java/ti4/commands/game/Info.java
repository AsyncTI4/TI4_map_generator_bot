package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
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
        GameManager gameManager = GameManager.getInstance();
        Game activeGame = getActiveGame();
        if (activeGame == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Game not found.");
            return;
        }

        OptionMapping gameNameOption = event.getOption(Constants.GAME_NAME);
        if (gameNameOption != null && !activeGame.getName().equalsIgnoreCase(gameNameOption.getAsString().toLowerCase())) {
            activeGame = gameManager.getGame(gameNameOption.getAsString().toLowerCase());
        }

        StringBuilder sb = getGameInfo(activeGame, event);
        MessageHelper.replyToMessage(event, sb.toString());
    }

    public static StringBuilder getGameInfo(Game activeGame, SlashCommandInteractionEvent event) {
        Boolean privateGame = FoWHelper.isPrivateGame(activeGame, event);

        StringBuilder sb = new StringBuilder();
        sb.append("## Game Info:").append(NEW_LINE);
        sb.append("### Name: ").append(activeGame.getName()).append(NEW_LINE);
        sb.append("Owner: ").append(activeGame.getOwnerName()).append(NEW_LINE);
        sb.append("Created: ").append(activeGame.getCreationDate()).append(NEW_LINE);
        sb.append("Last Modified: ").append(Helper.getDateRepresentation(activeGame.getLastModifiedDate())).append(NEW_LINE);
        sb.append("Ended: `").append(activeGame.isHasEnded()).append("`").append(NEW_LINE);
        if (activeGame.isHasEnded()) sb.append("> Date Ended: ").append(Helper.getDateRepresentation(activeGame.getEndedDate())).append(NEW_LINE);
        
        sb.append("### Setup: ").append(NEW_LINE);
        sb.append("VP Count: ").append(activeGame.getVp()).append(NEW_LINE);
        sb.append("Private Game: ").append(privateGame).append(NEW_LINE);
        sb.append("Game Modes: ").append(activeGame.getGameModesText()).append(NEW_LINE);
        if (!privateGame) {
            sb.append("Map String: `").append(Helper.getMapString(activeGame)).append("`").append(NEW_LINE);
        } else {
            sb.append("Map String: Cannot show map string for private games").append(NEW_LINE);
        }
        sb.append("Strategy Card Set: `").append(activeGame.getScSetID()).append("`").append(NEW_LINE);
        sb.append("Decks: ").append(NEW_LINE);
        sb.append("- ").append(Emojis.ActionCard).append("Action Card Deck: `").append(activeGame.getAcDeckID()).append("` ").append(activeGame.getActionCardDeckSize()).append("/").append(activeGame.getActionCardFullDeckSize()).append(NEW_LINE);
        sb.append("- ").append(Emojis.SecretObjective).append("Secret Objective Deck: `").append(activeGame.getSoDeckID()).append("` ").append(activeGame.getSecretObjectiveDeckSize()).append("/").append(activeGame.getSecretObjectiveFullDeckSize()).append(NEW_LINE);
        sb.append("- ").append(Emojis.Public1).append("Stage 1 Public Objective Deck: `").append(activeGame.getStage1PublicDeckID()).append("` ").append(activeGame.getPublicObjectives1DeckSize()).append("/").append(activeGame.getPublicObjectives1FullDeckSize()).append(NEW_LINE);
        sb.append("- ").append(Emojis.Public2).append("Stage 2 Public Objective Deck: `").append(activeGame.getStage2PublicDeckID()).append("` ").append(activeGame.getPublicObjectives2DeckSize()).append("/").append(activeGame.getPublicObjectives2FullDeckSize()).append(NEW_LINE);
        sb.append("- ").append(Emojis.Agenda).append("Agenda Deck: `").append(activeGame.getAgendaDeckID()).append("` ").append(activeGame.getAgendaDeckSize()).append("/").append(activeGame.getAgendaFullDeckSize()).append(NEW_LINE);
        if (activeGame.getEventDeckID() != null) sb.append("- ").append("").append("Event Deck: `").append(activeGame.getEventDeckID()).append("` ").append(activeGame.getEventDeckSize()).append("/").append(activeGame.getEventFullDeckSize()).append(NEW_LINE);
        sb.append("- ").append(Emojis.NonUnitTechSkip).append("Technology Deck: `").append(activeGame.getTechnologyDeckID()).append("`").append(NEW_LINE);
        sb.append("- ").append(Emojis.RelicCard).append("Relic Deck: `").append(activeGame.getRelicDeckID()).append("` ").append(activeGame.getRelicDeckSize()).append("/").append(activeGame.getRelicFullDeckSize()).append(NEW_LINE);
        sb.append("- Exploration Deck: `").append(activeGame.getExplorationDeckID()).append("`").append(NEW_LINE);
        sb.append(" - ").append(Emojis.IndustrialCard).append("Industrial Deck: ").append(activeGame.getIndustrialExploreDeckSize()).append("/").append(activeGame.getIndustrialExploreFullDeckSize()).append(NEW_LINE);
        sb.append(" - ").append(Emojis.HazardousCard).append("Hazardous Deck: ").append(activeGame.getHazardousExploreDeckSize()).append("/").append(activeGame.getHazardousExploreFullDeckSize()).append(NEW_LINE);
        sb.append(" - ").append(Emojis.CulturalCard).append("Cultural Deck: ").append(activeGame.getCulturalExploreDeckSize()).append("/").append(activeGame.getCulturalExploreFullDeckSize()).append(NEW_LINE);
        sb.append(" - ").append(Emojis.FrontierCard).append("Frontier Deck: ").append(activeGame.getFrontierExploreDeckSize()).append("/").append(activeGame.getFrontierExploreFullDeckSize()).append(NEW_LINE);
        
        sb.append("### Settings: ").append(NEW_LINE);
        sb.append("Beta Test Mode: ").append(activeGame.isTestBetaFeaturesMode()).append(NEW_LINE);
        sb.append("Auto-Ping Time Interval (hrs): ").append(activeGame.getAutoPingSpacer()).append(NEW_LINE);
        sb.append("Text Size: ").append(activeGame.getTextSize()).append(NEW_LINE);
        sb.append("Output Verbosity: ").append(activeGame.getOutputVerbosity()).append(NEW_LINE);
        if (activeGame.getTableTalkChannel() != null) sb.append("Table Talk Channel: ").append(activeGame.getTableTalkChannel().getAsMention()).append(NEW_LINE);
        if (activeGame.getActionsChannel() != null) sb.append("Actions Channel: ").append(activeGame.getActionsChannel().getAsMention()).append(NEW_LINE);
        if (activeGame.getBotMapUpdatesThread() != null) sb.append("Bot Map Thread: ").append(activeGame.getBotMapUpdatesThread().getAsMention()).append(NEW_LINE);

        if (!privateGame) {
            sb.append("### Players: ").append(NEW_LINE);
            int index = 1;
            for (Player player : activeGame.getRealPlayers()) {
                sb.append("> `").append(index).append(".` ").append(player.getFactionEmoji()).append(player.getUserName()).append(Helper.getColourAsMention(event.getGuild(), player.getColor()));
                if (player.getRoleForCommunity() != null) sb.append(" - Community Role: ").append(player.getRoleForCommunity().getName());
                sb.append(NEW_LINE);
                index++;
            }

            sb.append("### Other Players: ").append(NEW_LINE);
            for (Player player : activeGame.getNotRealPlayers()) {
                sb.append("> `").append(index).append(".` ").append(player.getUserName());
                sb.append(NEW_LINE);
                index++;
            }
        } else {
            sb.append("### Players: Cannot show players for private games").append(NEW_LINE);
        }
        
        sb.append("### Other Stats: ").append(NEW_LINE);
        sb.append("Current Phase: ").append(activeGame.getCurrentPhase()).append(NEW_LINE);
        sb.append("Ring Count: ").append(activeGame.getRingCount()).append(NEW_LINE);
        sb.append("Game Player Count: ").append(activeGame.getPlayerCountForMap()).append(NEW_LINE);
        sb.append("Game Real Player Count: ").append(activeGame.getRealPlayers().size()).append(NEW_LINE);
        sb.append("Map Images Generated: ").append(activeGame.getMapImageGenerationCount()).append(NEW_LINE);
        sb.append("SC Trade Goods: `").append(activeGame.getScTradeGoods()).append("`").append(NEW_LINE);
        sb.append("Public Objectives: `").append(activeGame.getRevealedPublicObjectives()).append("`").append(NEW_LINE);
        sb.append("Laws: `").append(activeGame.getLaws()).append("`").append(NEW_LINE);
        sb.append("Laws Info: `").append(activeGame.getLawsInfo()).append("`").append(NEW_LINE);
        sb.append("Migrations Run: `").append(activeGame.getRunMigrations()).append("`").append(NEW_LINE);
        sb.append("Buttons pressed: `").append(activeGame.getButtonPressCount()).append("`").append(NEW_LINE);

        return sb;
    }
}
