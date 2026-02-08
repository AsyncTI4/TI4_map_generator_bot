package ti4.commands.breakthrough;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.map.Player;

class BreakthroughInfo extends GameStateSubcommand {

    BreakthroughInfo() {
        super(Constants.INFO, "Send breakthrough information to your Cards Info channel", true, true);
        addOption(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction to see breakthroughs", false, true);
    }

    public void execute(SlashCommandInteractionEvent event) {
        Player p2 = CommandHelper.getOtherPlayerFromEvent(getGame(), event);
        BreakthroughCommandHelper.sendBreakthroughInfo(event, getGame(), getPlayer(), p2);
    }
}
