package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Player;

public class RefreshRelic extends ExhaustRelic {

    public RefreshRelic() {
        super(Constants.RELIC_REFRESH, "Ready a relic");
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to exhaust").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do edit\"").setRequired(false));
    }

    @Override
    protected void subAction(Player player, SlashCommandInteractionEvent event, String relicId) {
        player.removeExhaustedRelic(relicId);
        String relicName = Mapper.getRelic(relicId).getName();
        sendMessage("Refreshed " + Emojis.Relic + "Relic: " + relicName);
    }
}
