package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PurgeRelic extends GenericRelicAction {

    public PurgeRelic() {
        super(Constants.RELIC_PURGE, "Purge a relic", true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to exhaust").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do edit").setRequired(false));
    }

    @Override
    public void doAction(Player player, SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.RELIC);
        if (option == null) {
            MessageHelper.replyToMessage(event, "Specify relic");
            return;
        }
        String relicId = option.getAsString();
        if (player.getRelics().contains(relicId)) {
            player.removeRelic(relicId);
            player.removeExhaustedRelic(relicId);
            String relicName = Mapper.getRelic(relicId).split(";")[0];
            MessageHelper.replyToMessage(event, "Purged relic: " + relicName);
        } else {
            MessageHelper.replyToMessage(event, "Invalid relic or player does not have specified relic");
        }
    }
}
