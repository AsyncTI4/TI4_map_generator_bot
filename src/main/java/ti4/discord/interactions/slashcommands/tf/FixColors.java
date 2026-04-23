package ti4.discord.interactions.slashcommands.tf;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.slashcommands.GameStateSubcommand;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Constants;

class FixColors extends GameStateSubcommand {

    public FixColors() {
        super(Constants.FIX_COLORS, "Make the Mahact Kings match their faction", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ButtonHelperTwilightsFall.fixMahactColors(getGame(), event);
    }
}
