package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Expeditions;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class SetExpedition extends GameStateSubcommand {

    public SetExpedition() {
        super("set_expedition", "Set who did a certain expedition, enter null to clear", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction who completed the expedition or null")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.EXPEDITION, "The expedition")
                .setAutoComplete(true)
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String expedition = event.getOption(Constants.EXPEDITION).getAsString();
        String value = null;
        String faction = event.getOption(Constants.FACTION).getAsString();
        if (faction != null && !"null".equalsIgnoreCase(faction)) {
            value = faction;
        }
        Expeditions.setExpedition(game, expedition, value);
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Successfully set the value of the " + expedition + " expedition.");
    }
}
