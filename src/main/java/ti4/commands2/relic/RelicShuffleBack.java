package ti4.commands2.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RelicShuffleBack extends GameStateSubcommand {

    public RelicShuffleBack() {
        super(Constants.SHUFFLE_BACK, "Shuffle relic back into deck from player area", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to shuffle back into deck from player area").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        String relicId = event.getOption(Constants.RELIC).getAsString();
        if (player.hasRelic(relicId)) {
            player.removeRelic(relicId);
            player.removeExhaustedRelic(relicId);
            boolean success = getGame().shuffleRelicBack(relicId);
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
