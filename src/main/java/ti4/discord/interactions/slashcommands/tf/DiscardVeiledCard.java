package ti4.discord.interactions.slashcommands.tf;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.slashcommands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.VeiledHeartService;

class DiscardVeiledCard extends GameStateSubcommand {

    DiscardVeiledCard() {
        super(
                "discard_veiled_card",
                "Discard one or more of your veiled cards (Twilight's Fall: Veiled Heart mode)",
                true,
                true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color which will discard")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        VeiledHeartService.sendVeiledButtons(VeiledHeartService.VeiledCardAction.DISCARD, player);
    }
}
