package ti4.discord.interactions.commands.custom;

import java.util.function.BiPredicate;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class RemoveCardFromGame extends GameStateSubcommand {

    private final String optionName;
    private final BiPredicate<Game, String> removal;
    private final String successMessage;
    private final String failureMessage;

    private RemoveCardFromGame(
            String name,
            String description,
            boolean playerCommand,
            String optionName,
            String optionDescription,
            BiPredicate<Game, String> removal,
            String successMessage,
            String failureMessage) {
        super(name, description, true, playerCommand);
        markSuspicious();
        this.optionName = optionName;
        this.removal = removal;
        this.successMessage = successMessage;
        this.failureMessage = failureMessage;
        addOptions(new OptionData(OptionType.STRING, optionName, optionDescription)
                .setRequired(true)
                .setAutoComplete(true));
    }

    static RemoveCardFromGame actionCard() {
        return new RemoveCardFromGame(
                Constants.REMOVE_AC_FROM_GAME,
                "Remove an action card from the game",
                true,
                Constants.AC_ID,
                "Action card ID",
                Game::removeACFromGame,
                "Action card removed from game deck.",
                "Action card not found in game deck.");
    }

    static RemoveCardFromGame agenda() {
        return new RemoveCardFromGame(
                Constants.REMOVE_AGENDA_FROM_GAME,
                "Agenda remove from game",
                false,
                Constants.AGENDA_ID,
                "Agenda ID",
                Game::removeAgendaFromGame,
                "Agenda removed from game deck",
                "Agenda not found in game deck");
    }

    static RemoveCardFromGame discardAgenda() {
        return new RemoveCardFromGame(
                Constants.DISCARD_SPECIFIC_AGENDA,
                "Discard Specific Agenda",
                true,
                Constants.AGENDA_ID,
                "Agenda ID",
                Game::discardSpecificAgenda,
                "Agenda discarded from game deck",
                "Agenda not found in game deck");
    }

    static RemoveCardFromGame relic() {
        return new RemoveCardFromGame(
                Constants.REMOVE_RELIC_FROM_GAME,
                "Remove a relic from the game",
                true,
                Constants.RELIC,
                "Relic ID",
                Game::removeRelicFromGame,
                "Relic removed from game deck.",
                "Relic not found in game deck.");
    }

    static RemoveCardFromGame publicObjective() {
        return new RemoveCardFromGame(
                Constants.REMOVE_PO_FROM_GAME,
                "Remove a public objective from the game",
                true,
                Constants.PO_ID,
                "Public objective ID",
                Game::removePOFromGame,
                "Public objective removed from game deck.",
                "Public objective not found in game deck.");
    }

    static RemoveCardFromGame secretObjective() {
        return new RemoveCardFromGame(
                Constants.REMOVE_SO_FROM_GAME,
                "Removes a secret objective from the game",
                true,
                Constants.SO_ID,
                "Secret objective ID",
                Game::removeSOFromGame,
                "Secret objective removed from game deck.",
                "Secret objective not found in game deck.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean removed = removal.test(getGame(), event.getOption(optionName).getAsString());
        MessageHelper.sendMessageToChannel(event.getChannel(), removed ? successMessage : failureMessage);
    }
}
