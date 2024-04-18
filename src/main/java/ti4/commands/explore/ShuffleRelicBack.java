package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShuffleRelicBack extends GenericRelicAction {

    public ShuffleRelicBack() {
        super(Constants.SHUFFLE_BACK, "Shuffle relic back into deck from player area", true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to shuffle back into deck from player area").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do edit").setRequired(false));
    }

    @Override
    public void doAction(Player player, SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.RELIC);
        if (option == null) {
            MessageHelper.sendMessageToEventChannel(event, "Specify relic");
            return;
        }
        String relicId = option.getAsString();
        if (player.hasRelic(relicId)) {
            player.removeRelic(relicId);
            player.removeExhaustedRelic(relicId);
            boolean success = getActiveGame().shuffleRelicBack(relicId);
            if (success) {
                String relicName = Mapper.getRelic(relicId).getName();
                MessageHelper.sendMessageToEventChannel(event, "Shuffled relic back: " + relicName);
            } else {
                MessageHelper.sendMessageToEventChannel(event, "Could not shuffle relic back into deck.");
            }
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Invalid relic or player does not have specified relic");
        }
    }
}
