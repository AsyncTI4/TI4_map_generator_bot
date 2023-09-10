package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.player.Turn;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;

import ti4.map.Game;
import ti4.message.MessageHelper;

public class StartPhase extends GameSubcommandData {
    public StartPhase() {
        super(Constants.START_PHASE, "Start a specific phase of the game");
        addOptions(new OptionData(OptionType.STRING, Constants.SPECIFIC_PHASE, "What phase do you want to get buttons for?").setRequired(true).setAutoComplete(true));
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
            case "voting" -> AgendaHelper.startTheVoting(activeGame, event);
            case "finSpecial" -> ButtonHelper.fixRelics(activeGame);
            case "statusScoring" -> {
                new Turn().showPublicObjectivesWhenAllPassed(event, activeGame, activeGame.getMainGameChannel());
                activeGame.updateActivePlayer(null);
            }
            case "statusHomework" -> ButtonHelper.startStatusHomework(event, activeGame);
            case "agendaResolve" -> AgendaHelper.resolveTime(event, activeGame, null);
            case "action" -> ButtonHelper.startActionPhase(event, activeGame);
            default -> MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find phase: `" + phase + "`");
        }
    }
}
