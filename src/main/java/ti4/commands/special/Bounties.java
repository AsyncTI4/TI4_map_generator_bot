package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;

class Bounties extends GameStateSubcommand {

    Bounties() {
        super(Constants.BOUNTIES, "Show current bounties and manage bounty tokens", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        ButtonHelperAbilities.offerBountyButtons(game, player);
    }
}
