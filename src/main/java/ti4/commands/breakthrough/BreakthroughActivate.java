package ti4.commands.breakthrough;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;

public class BreakthroughActivate extends GameStateSubcommand {
    public BreakthroughActivate() {
        super(Constants.BREAKTHROUGH_ACTIVATE, "Activate (or unactivate) breakthrough", true, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        BreakthroughModel bt = player.getBreakthroughModel();
        if (bt == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Player does not have a breakthrough");
        } else {
            boolean active = player.isBreakthroughActive();
            player.setBreakthroughActive(!active);
            String message = player.getRepresentation() + (active ? " de-" : " ") + "activated their breakthrough " + bt.getName();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        }
    }
}
