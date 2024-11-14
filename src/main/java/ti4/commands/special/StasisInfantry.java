package ti4.commands.special;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.player.Stats;
import ti4.commands2.CommandHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class StasisInfantry extends SpecialSubcommandData {
    public StasisInfantry() {
        super(Constants.STASIS_INFANTRY, "Add/Remove Infantry to Stasis Capsule");
        addOptions(new OptionData(OptionType.STRING, Constants.COUNT, "Infantry count").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping option = event.getOption(Constants.COUNT);
        if (option != null) {
            setValue(event, game, player, player::setStasisInfantry, player::getGenSynthesisInfantry, option.getAsString());
        }

    }

    public void setValue(SlashCommandInteractionEvent event, Game game, Player player, Consumer<Integer> consumer, Supplier<Integer> supplier, String value) {
        try {
            boolean setValue = !value.startsWith("+") && !value.startsWith("-");
            int number = Integer.parseInt(value);
            int existingNumber = supplier.get();
            String explanation = "";
            if (setValue) {
                consumer.accept(number);
                String messageToSend = Stats.getSetValueMessage(event, player, Constants.COUNT, number, existingNumber, explanation);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), messageToSend);
            } else {
                int newNumber = existingNumber + number;
                newNumber = Math.max(newNumber, 0);
                consumer.accept(newNumber);
                String messageToSend = Stats.getChangeValueMessage(event, player, Constants.COUNT, number, existingNumber, newNumber, explanation);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), messageToSend);
            }
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse number for: " + Constants.COUNT);
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        SpecialCommand.reply(event);
    }
}
