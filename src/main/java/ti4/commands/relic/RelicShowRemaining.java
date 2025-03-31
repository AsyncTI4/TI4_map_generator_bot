package ti4.commands.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.RelicHelper;

class RelicShowRemaining extends GameStateSubcommand {

    public RelicShowRemaining() {
        super(Constants.SHOW_REMAINING, "Show remaining relics in deck", false, true);
        addOptions(new OptionData(OptionType.STRING, Constants.OVERRIDE_FOW, "TRUE if override fog"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping override = event.getOption(Constants.OVERRIDE_FOW);
        boolean over = false;
        if (override != null) {
            over = "TRUE".equalsIgnoreCase(override.getAsString());
        }
        RelicHelper.showRemaining(event.getMessageChannel(), over, getGame(), getPlayer());
    }
}
