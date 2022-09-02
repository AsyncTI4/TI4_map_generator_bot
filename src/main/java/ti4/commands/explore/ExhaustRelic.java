package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ExhaustRelic extends GenericRelicAction {

    public ExhaustRelic() {
        super(Constants.RELIC_EXHAUST, "Exhaust a relic", true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to exhaust").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
    }

	public ExhaustRelic(String relicRefresh, String refresh_a_relic) {
		super(relicRefresh, refresh_a_relic, true);
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
			subAction(player, event, relicId);
		} else {
            MessageHelper.replyToMessage(event, "Invalid relic or player does not have specified relic");
        }
    }

	protected void subAction(Player player, SlashCommandInteractionEvent event, String relicId) {
		player.addExhaustedRelic(relicId);
		String relicName = Mapper.getRelic(relicId).split(";")[0];
		MessageHelper.replyToMessage(event, "Exhausted relic: " + relicName);
	}
}
