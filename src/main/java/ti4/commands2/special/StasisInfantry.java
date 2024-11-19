package ti4.commands2.special;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.stats.StatsService;

class StasisInfantry extends GameStateSubcommand {

    public StasisInfantry() {
        super(Constants.STASIS_INFANTRY, "Add/Remove Infantry to Stasis Capsule", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.COUNT, "Infantry count").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        String count = event.getOption(Constants.COUNT).getAsString();
        setValue(event, player, player::setStasisInfantry, player::getGenSynthesisInfantry, count);

    }

    public void setValue(SlashCommandInteractionEvent event, Player player, Consumer<Integer> consumer, Supplier<Integer> supplier, String value) {
        try {
            boolean setValue = !value.startsWith("+") && !value.startsWith("-");
            int number = Integer.parseInt(value);
            int existingNumber = supplier.get();
            String explanation = "";
            if (setValue) {
                consumer.accept(number);
                String messageToSend = StatsService.getSetValueMessage(event, player, Constants.COUNT, number, existingNumber, explanation);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), messageToSend);
            } else {
                int newNumber = existingNumber + number;
                newNumber = Math.max(newNumber, 0);
                consumer.accept(newNumber);
                String messageToSend = StatsService.getChangeValueMessage(event, player, Constants.COUNT, number, existingNumber, newNumber, explanation);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), messageToSend);
            }
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse number for: " + Constants.COUNT);
        }
    }
}
