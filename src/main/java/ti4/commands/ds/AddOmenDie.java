package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class AddOmenDie extends GameStateSubcommand {

    public AddOmenDie() {
        super(Constants.ADD_OMEN_DIE, "Add a Omen Die", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.RESULT, "Number on the Omen Die").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int dieResult = event.getOption(Constants.RESULT, 1, OptionMapping::getAsInt);
        ButtonHelperAbilities.addOmenDie(getGame(), dieResult);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Added an Omen Die with value " + dieResult);
    }
}
