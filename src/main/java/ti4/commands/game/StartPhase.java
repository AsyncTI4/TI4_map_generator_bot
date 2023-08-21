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

import ti4.map.Map;
import ti4.message.MessageHelper;

public class StartPhase extends GameSubcommandData {
    public StartPhase() {
        super(Constants.START_PHASE, "Start a specific phase of the game");
        addOptions(new OptionData(OptionType.STRING, Constants.SPECIFIC_PHASE, "What phase do you want to get buttons for?").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        String phase = event.getOption(Constants.SPECIFIC_PHASE, null, OptionMapping::getAsString);
        startPhase(event, activeMap, phase);
    }

    public static void startPhase(GenericInteractionCreateEvent event, Map activeMap, String phase) {
        switch (phase) {
            case "strategy" -> {
                ButtonHelper.startStrategyPhase(event, activeMap);
            }
            case "voting" -> {
                AgendaHelper.startTheVoting(activeMap, event);
            }
            case "finSpecial" -> {
                ButtonHelper.fixRelics(activeMap);
            }
            case "statusScoring" -> {
                new Turn().showPublicObjectivesWhenAllPassed(event, activeMap, activeMap.getMainGameChannel());
                activeMap.updateActivePlayer(null);
            }
            case "statusHomework" -> {
                ButtonHelper.startStatusHomework(event, activeMap);
            }
            case "agendaResolve" -> {
                AgendaHelper.resolveTime(event, activeMap, null);
            }
            case "action" -> {
                ButtonHelper.startActionPhase(event, activeMap);
            }
            default -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find phase: `" + phase + "`");
            }
        }
    }
}
