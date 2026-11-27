package ti4.discord.interactions.commands.homebrew.lost_legacies;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn.StonebornAbilityHandler;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class RemoveFragmentumToken extends GameStateSubcommand {
    RemoveFragmentumToken() {
        super("remove_fragmentum_token", "Remove a Fragmentum token from a player", true, true);
        addOptions(AddFragmentumToken.fragmentumColorOption());
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        String trait = event.getOption("color").getAsString();
        if (!StonebornAbilityHandler.removeFragmentumToken(game, player, trait)) {
            MessageHelper.sendMessageToEventChannel(event, "That player does not have that Fragmentum token.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " lost 1 " + StonebornAbilityHandler.getFragmentumTokenColor(trait)
                        + " Fragmentum token.");
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
