package ti4.discord.interactions.commands.leaders;

import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;

abstract class LeaderSubcommand extends GameStateSubcommand {

    LeaderSubcommand(String name, String description) {
        super(name, description, true, true);
        addOptions(CommonOptions.leader());
        addOptions(CommonOptions.factionColorForStats());
    }
}
