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
      sendMessage(Helper.getPlayerRepresentation(player, getActiveGame()) + " purged relic Relic:\n" + Emojis.Relic + " __**" + relicData[0] + "**__\n> " + relicData[1] + "\n");
        RelicInfo.sendRelicInfo(getActiveGame(), player, event);
    }
}
