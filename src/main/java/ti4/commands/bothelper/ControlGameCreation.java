package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class ControlGameCreation extends BothelperSubcommandData {
    public ControlGameCreation() {
        super(Constants.CONTROL_GAME_CREATION, "Stop or allow game creation buttons to be pressed");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ALLOW_GAME_CREATION,
            "True to allow the button to be pressed").setRequired(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // GAME NAME
        Game mapreference = GameManager.getInstance().getGame("finreference");
        Boolean light = event.getOption(Constants.ALLOW_GAME_CREATION, null, OptionMapping::getAsBoolean);
        if (light != null && !light) {
            mapreference.setStoredValue("allowedButtonPress", "false");
            MessageHelper.sendMessageToChannel(event.getChannel(), "Set game creation button presses as unallowed");
        } else {
            mapreference.setStoredValue("allowedButtonPress", "true");
            MessageHelper.sendMessageToChannel(event.getChannel(), "Set game creation button presses as allowed");
        }
        GameSaveLoadManager.saveGame(mapreference, "Updated Setting");
    }
}
