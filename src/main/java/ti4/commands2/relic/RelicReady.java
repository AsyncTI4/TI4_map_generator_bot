package ti4.commands2.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;

class RelicReady extends GameStateSubcommand {

    public RelicReady() {
        super(Constants.RELIC_REFRESH, "Ready a relic", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to be readied").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        String relicId = event.getOption(Constants.RELIC).getAsString();
        if (player.hasRelic(relicId)) {
            player.removeExhaustedRelic(relicId);
            String relicName = Mapper.getRelic(relicId).getName();
            MessageHelper.sendMessageToEventChannel(event, "Readied _" + relicName + "_.");
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Invalid relic or player does not have specified relic.");
        }
    }
}
