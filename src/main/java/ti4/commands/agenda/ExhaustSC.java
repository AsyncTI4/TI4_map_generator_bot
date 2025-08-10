package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class ExhaustSC extends GameStateSubcommand {

    public ExhaustSC() {
        super(Constants.EXHAUST_SC, "Exhaust a strategy card due to Absol agenda (or undo this)", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC, "Strategy card you wish to exhaust")
                .setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.UNDO, "True to ready (undo) instead of exhaust"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option2 = event.getOption(Constants.UNDO);
        boolean undo = false;
        if (option2 != null) {
            undo = option2.getAsBoolean();
        }

        OptionMapping option = event.getOption(Constants.SC);
        int sc = option.getAsInt();
        Game game = getGame();
        if (!undo) {
            getGame().setStoredValue("exhaustedSC" + sc, "Exhausted");
            MessageHelper.sendMessageToChannel(event.getChannel(), "Exhausted **" + Helper.getSCName(sc, game) + "**.");
        } else {
            getGame().setStoredValue("exhaustedSC" + sc, "");
            MessageHelper.sendMessageToChannel(event.getChannel(), "Readied **" + Helper.getSCName(sc, game) + "**.");
        }
    }
}
