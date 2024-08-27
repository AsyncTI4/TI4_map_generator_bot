package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ExhaustSC extends AgendaSubcommandData {
    public ExhaustSC() {
        super(Constants.EXHAUST_SC, "Exhaust am SC due to absol agenda (or undo this)");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC, "SC you wish to exhaust").setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.UNDO, "True to refresh (undo) instead of exhaust").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping option = event.getOption(Constants.SC);

        int sc = option.getAsInt();

        OptionMapping option2 = event.getOption(Constants.UNDO);
        boolean undo = false;
        if (option2 != null) {
            undo = option2.getAsBoolean();
        }
        if (!undo) {
            game.setStoredValue("exhaustedSC" + sc, "Exhausted");
            MessageHelper.sendMessageToChannel(event.getChannel(), "Exhausted SC #" + sc);
        } else {
            game.setStoredValue("exhaustedSC" + sc, "");
            MessageHelper.sendMessageToChannel(event.getChannel(), "Refreshed SC #" + sc);
        }

    }
}
