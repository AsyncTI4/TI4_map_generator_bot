package ti4.discord.interactions.commands.event;

import java.util.function.BiPredicate;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class EventInEffect extends GameStateSubcommand {

    private final BiPredicate<Game, Integer> change;
    private final String successMessage;

    private EventInEffect(String name, String description, BiPredicate<Game, Integer> change, String successMessage) {
        super(name, description, true, true);
        this.change = change;
        this.successMessage = successMessage;
        addOptions(new OptionData(OptionType.INTEGER, Constants.EVENT_ID, "Event ID, which is found between ()")
                .setRequired(true));
    }

    static EventInEffect add() {
        return new EventInEffect(
                Constants.ADD_EVENT,
                "Add event as if it were a law (permanent/temporary)",
                Game::addEventInEffect,
                "Event added.");
    }

    static EventInEffect remove() {
        return new EventInEffect(Constants.REMOVE_EVENT, "Remove event", Game::removeEventInEffect, "Event removed.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Integer eventID = event.getOption(Constants.EVENT_ID, null, OptionMapping::getAsInt);
        boolean success = change.test(getGame(), eventID);
        MessageHelper.sendMessageToChannel(event.getChannel(), success ? successMessage : "Event ID not found.");
    }
}
