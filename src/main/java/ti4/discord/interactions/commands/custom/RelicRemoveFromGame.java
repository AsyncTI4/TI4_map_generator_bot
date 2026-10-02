package ti4.discord.interactions.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class RelicRemoveFromGame extends GameStateSubcommand {

    RelicRemoveFromGame() {
        super(Constants.REMOVE_RELIC_FROM_GAME, "Remove a relic from the game", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic ID")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean removed =
                getGame().removeRelicFromGame(event.getOption(Constants.RELIC).getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Relic removed from game deck.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Relic not found in game deck.");
        }
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
