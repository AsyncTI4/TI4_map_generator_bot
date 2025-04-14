package ti4.commands.omegaphase;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.omegaPhase.VoiceOfTheCouncilHelper;

class ResetVoiceOfTheCouncil extends GameStateSubcommand {
    public ResetVoiceOfTheCouncil() {
        super(Constants.RESET_VOICE_OF_THE_COUNCIL, "Unscore and reset Voice of the Council", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        VoiceOfTheCouncilHelper.ResetVoiceOfTheCouncil(getGame());
    }
}