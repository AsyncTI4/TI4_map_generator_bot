package ti4.discord.interactions.commands.tf;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.VeiledHeartService;

class DoVeiledCardAction extends GameStateSubcommand {

    DoVeiledCardAction() {
        super(
                "veiled_card",
                "Draw/Discard/Unveil a specified veiled card (Twilight's Fall: Veiled Heart mode)",
                true,
                true);
        addOptions(
                new OptionData(OptionType.STRING, "action", "Action Type (Draw/Discard/Unveil)", true)
                        .addChoices(
                                new Command.Choice("Draw", "draw"),
                                new Command.Choice("Discard", "discard"),
                                new Command.Choice("Unveil", "unveil")),
                new OptionData(OptionType.STRING, "card", "Card to perform the action on", true),
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color performing the action")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        String actionStr = event.getOption("action", OptionMapping::getAsString);
        String card = event.getOption("card", OptionMapping::getAsString);
        VeiledHeartService.doAction(actionStr, player, card);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
