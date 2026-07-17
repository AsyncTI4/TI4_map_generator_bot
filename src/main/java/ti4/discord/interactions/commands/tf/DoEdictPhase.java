package ti4.discord.interactions.commands.tf;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.buttons.handlers.edict.EdictPhaseHandler;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;

class DoEdictPhase extends GameStateSubcommand {

    public DoEdictPhase() {
        super(Constants.DO_EDICT_PHASE, "Do the Edict Phase", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        EdictPhaseHandler.edictPhase(event, getGame());
    }
}
