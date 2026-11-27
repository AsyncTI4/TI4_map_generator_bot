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

class StonebornUnleash extends GameStateSubcommand {
    StonebornUnleash() {
        super("unleash", "Choose a Stoneborn component to UNLEASH", true, true);
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        if (!StonebornAbilityHandler.offerAdaptiveComponentButtons(game, player, player.getCorrectChannel())) {
            MessageHelper.sendMessageToEventChannel(event, "That player has no Stoneborn component they can UNLEASH.");
        }
    }
}
