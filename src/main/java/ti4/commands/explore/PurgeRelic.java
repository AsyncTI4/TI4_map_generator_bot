package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Player;

public class PurgeRelic extends GenericRelicAction {

    public PurgeRelic() {
        super(Constants.RELIC_PURGE, "Purge a relic", true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to purge").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void doAction(Player player, SlashCommandInteractionEvent event) {
        String relicId = event.getOption(Constants.RELIC, null, OptionMapping::getAsString);
        if (relicId == null || !player.hasRelic(relicId)) {
            sendMessage("Invalid relic or player does not have specified relic: " + relicId);
            return;
        }
        player.removeRelic(relicId);
        player.removeExhaustedRelic(relicId);
        String[] relicData = Mapper.getRelic(relicId).split(";");
        StringBuilder message = new StringBuilder();
        message.append(Helper.getPlayerRepresentation(player, getActiveMap())).append(" purged relic Relic:\n").append(Emojis.Relic).append(" __**").append(relicData[0]).append("**__\n> ").append(relicData[1]).append("\n");
        sendMessage(message.toString());
    }
}
