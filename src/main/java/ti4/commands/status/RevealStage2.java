package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.RevealPublicObjectiveService;

class RevealStage2 extends GameStateSubcommand {

    public RevealStage2() {
        super(Constants.REVEAL_STAGE2, "Reveal Stage2 Public Objective", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        RevealPublicObjectiveService.revealS2(getGame(), event, event.getChannel());
    }
}
