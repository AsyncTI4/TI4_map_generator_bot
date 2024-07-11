package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.player.TurnEnd;
import ti4.commands.status.ListPlayerInfoButton;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class StartPhase extends GameSubcommandData {
    public StartPhase() {
        super(Constants.START_PHASE, "Start a specific phase of the game");
        addOptions(new OptionData(OptionType.STRING, Constants.SPECIFIC_PHASE,
            "What phase do you want to get buttons for?").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        String phase = event.getOption(Constants.SPECIFIC_PHASE, null, OptionMapping::getAsString);
        startPhase(event, game, phase);
    }

    public static void startPhase(GenericInteractionCreateEvent event, Game game, String phase) {
        switch (phase) {
            case "strategy" -> ButtonHelper.startStrategyPhase(event, game);
            case "voting" -> AgendaHelper.startTheVoting(game);
            case "finSpecial" -> ButtonHelper.fixAllianceMembers(game);
            case "publicObj" -> ListPlayerInfoButton.displayerScoringProgression(game, true, event, "both");
            case "publicObjAll" -> ListPlayerInfoButton.displayerScoringProgression(game, false, event, "1");
            // case "unleashTheNames" -> OtherStats.sendAllNames(event);
            // case "unleashTheNamesDS" -> OtherStats.sendAllNames(event, true, false);
            // case "unleashTheNamesAbsol" -> OtherStats.sendAllNames(event, false, true);
            // case "unleashTheNamesEnded" -> OtherStats.showGameLengths(event, 120);
            case "ixthian" -> AgendaHelper.rollIxthian(game, false);
            case "gameTitles" -> ButtonHelper.offerEveryoneTitlePossibilities(game);
            case "giveAgendaButtonsBack" -> Helper.giveMeBackMyAgendaButtons(game);
            case "finSpecialSomnoFix" -> Helper.addBotHelperPermissionsToGameChannels(event);
            case "finSpecialAbsol" -> AgendaHelper.resolveAbsolAgainstChecksNBalances(game);
            case "finFixSecrets" -> game.fixScrewedSOs();
            case "statusScoring" -> {
                TurnEnd.showPublicObjectivesWhenAllPassed(event, game, game.getMainGameChannel());
                game.updateActivePlayer(null);
            }
            case "endOfGameSummary" -> {
                String endOfGameSummary = "";

                for (int x = 1; x < game.getRound() + 1; x++) {
                    String summary = "";
                    for (Player player : game.getRealPlayers()) {
                        if (!game.getStoredValue("endofround" + x + player.getFaction()).isEmpty()) {
                            summary = summary + player.getFactionEmoji() + ": " + game.getStoredValue("endofround" + x + player.getFaction()) + "\n";
                        }
                    }
                    if (!summary.isEmpty()) {
                        summary = "**__Round " + x + " Summary__**\n" + summary;
                        endOfGameSummary = endOfGameSummary + summary;
                    }
                }
                if (!endOfGameSummary.isEmpty()) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), endOfGameSummary);
                }
            }
            case "statusHomework" -> ButtonHelper.startStatusHomework(event, game);
            case "agendaResolve" -> AgendaHelper.resolveTime(event, game, null);
            case "pbd1000decks" -> {
                game.pbd1000decks();
                GameSaveLoadManager.saveMap(game, event);
            }
            case "action" -> ButtonHelper.startActionPhase(event, game);
            case "playerSetup" -> ButtonHelper.offerPlayerSetupButtons(event.getMessageChannel(), game);
            default -> MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find phase: `" + phase + "`");
        }
    }
}
