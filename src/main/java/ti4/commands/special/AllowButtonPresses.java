package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class AllowButtonPresses extends GameStateSubcommand {

    public AllowButtonPresses() {
        super(Constants.ALLOW_BUTTON_PRESSES, "Allow or disallow others to press buttons for a player", true, true);
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ENABLE, "Enable or disable allowing others to press buttons"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player to update"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to update").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        boolean enable = event.getOption(Constants.ENABLE, true, OptionMapping::getAsBoolean);
        player.setAllowOthersToPressButtons(enable);
        String status = enable ? "enabled" : "disabled";
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + " has " + status + " button presses by others.");
    }
}
