package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class AddOmenDie extends GameStateSubcommand {

    public AddOmenDie() {
        super(Constants.ADD_OMEN_DIE, "Add a Omen Die", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.RESULT, "Number on the Omen Die").setRequired(true));
        // addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALL_ASYNC_TILES, "True to include all async blue back tiles in this list (not just PoK + DS). Default: false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        int dieResult = event.getOption(Constants.RESULT, 1, OptionMapping::getAsInt);
        ButtonHelperAbilities.addOmenDie(game, dieResult);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Added an Omen Die with value " + dieResult);
    }

}
