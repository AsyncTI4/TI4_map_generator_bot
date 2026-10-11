package ti4.discord.interactions.commands.tf;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.service.VeiledHeartService;

class DiscardVeiledCard extends GameStateSubcommand {

    DiscardVeiledCard() {
        super(
                "discard_veiled_card",
                "Discard one or more of your veiled cards (Twilight's Fall: Veiled Heart mode)",
                true,
                true);
        addOptions(CommonOptions.factionColor("Faction or Color which will discard"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        VeiledHeartService.sendVeiledButtons(VeiledHeartService.VeiledCardAction.DISCARD, player);
    }
}
