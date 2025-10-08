package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Expeditions;
import ti4.map.Game;
import ti4.map.Player;

public class SetExpedition extends GameStateSubcommand {

    public SetExpedition() {
        super("set_expedition", "Set who did a certain expedition, enter null to clear", true, false);
        addOptions(new OptionData(
                        OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color which completed the expedition")
                .setAutoComplete(true)
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.EXPEDITION, "The expedition")
                .setAutoComplete(true)
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String expedition = event.getOption(Constants.EXPEDITION).getAsString();
        Player player = getPlayer();
        String value = null;
        if (player != null && !player.getFaction().equalsIgnoreCase("null")) {
            value = player.getFaction();
        }
        Expeditions.setExpedition(game, expedition, value);
    }
}
