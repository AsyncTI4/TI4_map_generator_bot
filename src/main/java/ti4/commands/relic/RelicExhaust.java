package ti4.commands.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;

class RelicExhaust extends GameStateSubcommand {

    public RelicExhaust() {
        super(Constants.RELIC_EXHAUST, "Exhaust a Relic", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to exhaust")
                .setAutoComplete(true)
                .setRequired(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        String relicId = event.getOption(Constants.RELIC).getAsString();
        if (player.hasRelic(relicId)) {
            player.addExhaustedRelic(relicId);
            String relicName = Mapper.getRelic(relicId).getName();
            MessageHelper.sendMessageToEventChannel(
                    event, player.getRepresentationNoPing() + " exhausted the relic _" + relicName + "_.");
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Invalid relic or player does not have specified relic");
        }
    }
}
