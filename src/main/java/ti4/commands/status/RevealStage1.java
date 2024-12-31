package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.objectives.RevealPublicObjectiveService;

class RevealStage1 extends GameStateSubcommand {

    public RevealStage1() {
        super(Constants.REVEAL_STAGE1, "Reveal Stage1 Public Objective", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        RevealPublicObjectiveService.revealS1(getGame(), event, event.getChannel());
    }
}
