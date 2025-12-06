package ti4.commands.tf;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class DrawSpecificParadigm extends GameStateSubcommand {

    DrawSpecificParadigm() {
        super("draw_specific_paradigm", "Draw a specific Twilight's Fall Paradigm", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TF_PARADIGM, "Name of the paradigm you wish to draw")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you draw")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        String paradigm = event.getOption(Constants.TF_PARADIGM).getAsString();
        boolean success = ButtonHelperTwilightsFall.drawSpecificParadigm(game, player, paradigm, true, false);

        String msg = player.getRepresentationNoPing() + " ";
        msg += success
                ? " successfully drew a specific paradigm."
                : " tried to draw a specific paradigm, but was unable to.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
