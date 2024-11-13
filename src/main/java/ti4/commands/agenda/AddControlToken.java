package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class AddControlToken extends GameStateSubcommand {

    public AddControlToken() {
        super(Constants.ADD_CONTROL_TOKEN, "Add or remove a faction control token to a law", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction that owns the token, default you").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.UNDO, "True to remove instead of add tokens").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var option = event.getOption(Constants.AGENDA_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda ID defined");
            return;
        }

        var game = getGame();
        int lawId = option.getAsInt();
        String msg = game.getStoredValue("controlTokensOnAgenda" + lawId);
        boolean undo = false;
        var option2 = event.getOption(Constants.UNDO);
        if (option2 != null) {
            undo = option2.getAsBoolean();
        }

        var player = getPlayer();
        if (!undo) {
            if (msg.isEmpty()) {
                msg = player.getColor();
            } else {
                msg += "_" + player.getColor();
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), "Added control token to law #" + lawId);
        } else {
            if (!msg.isEmpty()) {
                msg = msg.replaceFirst(player.getColor() + "_", "");
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), "Removed control token to law #" + lawId);

        }

        game.setStoredValue("controlTokensOnAgenda" + lawId, msg);
    }
}
