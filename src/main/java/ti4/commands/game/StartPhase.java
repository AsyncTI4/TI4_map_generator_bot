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
        Game activeGame = getActiveGame();
        String phase = event.getOption(Constants.SPECIFIC_PHASE, null, OptionMapping::getAsString);
        startPhase(event, activeGame, phase);
    }

    public static void startPhase(GenericInteractionCreateEvent event, Game activeGame, String phase) {
        switch (phase) {
            case "strategy" -> ButtonHelper.startStrategyPhase(event, activeGame);
            case "voting" -> AgendaHelper.startTheVoting(activeGame);
            case "finSpecial" -> ButtonHelper.fixAllianceMembers(activeGame);
            case "publicObj" -> ListPlayerInfoButton.displayerScoringProgression(activeGame, true, event, "both");
            case "publicObjAll" -> ListPlayerInfoButton.displayerScoringProgression(activeGame, false, event, "1");
            // case "unleashTheNames" -> OtherStats.sendAllNames(event);
            // case "unleashTheNamesDS" -> OtherStats.sendAllNames(event, true, false);
            // case "unleashTheNamesAbsol" -> OtherStats.sendAllNames(event, false, true);
            // case "unleashTheNamesEnded" -> OtherStats.showGameLengths(event, 120);
            case "ixthian" -> AgendaHelper.rollIxthian(activeGame, false);
            case "gameTitles" -> ButtonHelper.offerEveryoneTitlePossibilities(activeGame);
            case "giveAgendaButtonsBack" -> Helper.giveMeBackMyAgendaButtons(activeGame);
            case "finSpecialSomnoFix" -> Helper.addBotHelperPermissionsToGameChannels(event);
            case "finSpecialAbsol" -> AgendaHelper.resolveAbsolAgainstChecksNBalances(activeGame);
            case "finFixSecrets" -> activeGame.fixScrewedSOs();
            case "statusScoring" -> {
                TurnEnd.showPublicObjectivesWhenAllPassed(event, activeGame, activeGame.getMainGameChannel());
                activeGame.updateActivePlayer(null);
            }
            case "endOfGameSummary" -> {
                String endOfGameSummary = "";

                for (int x = 1; x < activeGame.getRound() + 1; x++) {
                    String summary = "";
                    for (Player player : activeGame.getRealPlayers()) {
                        if (!activeGame.getStoredValue("endofround" + x + player.getFaction()).isEmpty()) {
                            summary = summary + player.getFactionEmoji() + ": " + activeGame.getStoredValue("endofround" + x + player.getFaction()).replace("666fin", ":").replace("667fin", ",") + "\n";
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
            case "statusHomework" -> ButtonHelper.startStatusHomework(event, activeGame);
            case "agendaResolve" -> AgendaHelper.resolveTime(event, activeGame, null);
            case "action" -> ButtonHelper.startActionPhase(event, activeGame);
            case "playerSetup" -> ButtonHelper.offerPlayerSetupButtons(event.getMessageChannel(), activeGame);
            default -> MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find phase: `" + phase + "`");
        }
    }
}
