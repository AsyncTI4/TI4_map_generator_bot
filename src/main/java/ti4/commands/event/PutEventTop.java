package ti4.commands.event;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PutEventTop extends PlayerGameStateSubcommand {

    public PutEventTop() {
        super(Constants.PUT_TOP, "Put Event on top", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.EVENT_ID, "Event ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Integer numericalID = event.getOption(Constants.EVENT_ID, null, OptionMapping::getAsInt);
        Game game = getGame();
        Player player = getPlayer();
        putTop(numericalID, game, player);
    }

    public void putTop(int eventID, Game game, Player player) {
        boolean success = game.putEventTop(eventID, player);
        if (success) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Event put on top");
        } else {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "No Event ID found");
        }
    }
}
