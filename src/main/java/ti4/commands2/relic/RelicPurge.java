package ti4.commands2.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

class RelicPurge extends GameStateSubcommand {

    public RelicPurge() {
        super(Constants.RELIC_PURGE, "Purge a relic", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to purge").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        String relicId = event.getOption(Constants.RELIC, null, OptionMapping::getAsString);
        if (relicId == null || !player.hasRelic(relicId)) {
            MessageHelper.sendMessageToEventChannel(event, "Invalid relic or player does not have specified relic: " + relicId);
            return;
        }
        player.removeRelic(relicId);
        player.removeExhaustedRelic(relicId);
        RelicModel relicData = Mapper.getRelic(relicId);
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + " purged relic Relic:\n" + Emojis.Relic + " __**" + relicData.getName() + "**__\n> " + relicData.getText() + "\n");
        RelicInfo.sendRelicInfo(getGame(), player, event);
    }
}
