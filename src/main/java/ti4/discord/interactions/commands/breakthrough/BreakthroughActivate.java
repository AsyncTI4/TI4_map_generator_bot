package ti4.discord.interactions.commands.breakthrough;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;

class BreakthroughActivate extends GameStateSubcommand {

    BreakthroughActivate() {
        super(Constants.BREAKTHROUGH_ACTIVATE, "Activate (or deactivate) breakthrough", true, true);
        addOption(OptionType.STRING, Constants.BREAKTHROUGH, "Which breakthrough to (de-)activate", false, true);
        addOption(OptionType.STRING, Constants.FACTION_COLOR, "Faction to activate their breakthrough", false, true);
    }

    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        List<String> ids = BreakthroughCommandHelper.getBreakthroughsFromEvent(event, player);
        BreakthroughCommandHelper.activateBreakthroughs(event, player, ids);
    }
}
