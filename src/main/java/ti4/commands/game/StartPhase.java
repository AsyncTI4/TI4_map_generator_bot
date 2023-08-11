package ti4.commands.game;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.player.Turn;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class StartPhase extends GameSubcommandData {
    public StartPhase() {
        super(Constants.START_PHASE, "Start a specific phase of the game");
        addOptions(new OptionData(OptionType.STRING, Constants.SPECIFIC_PHASE, "What phase do you want to get buttons for?").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping codexOption = event.getOption(Constants.SPECIFIC_PHASE);
        if (codexOption != null) {
            String codex = codexOption.getAsString();
            switch(codex){
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
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find that phase");
                }
            }
        }
    }
}
