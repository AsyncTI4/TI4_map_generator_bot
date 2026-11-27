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

class AddFragmentumToken extends GameStateSubcommand {
    AddFragmentumToken() {
        super("add_fragmentum_token", "Add a Fragmentum token to a player", true, true);
        addOptions(fragmentumColorOption());
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        String trait = event.getOption("color").getAsString();
        if (!StonebornAbilityHandler.addFragmentumToken(game, player, trait)) {
            MessageHelper.sendMessageToEventChannel(event, "That Fragmentum token cannot be added.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gained 1 " + StonebornAbilityHandler.getFragmentumTokenColor(trait)
                        + " Fragmentum token.");
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }

    static OptionData fragmentumColorOption() {
        return new OptionData(OptionType.STRING, "color", "Fragmentum token color")
                .setRequired(true)
                .addChoice("Blue", "cultural")
                .addChoice("Red", "hazardous")
                .addChoice("Green", "industrial")
                .addChoice("Gray", "frontier");
    }
}
