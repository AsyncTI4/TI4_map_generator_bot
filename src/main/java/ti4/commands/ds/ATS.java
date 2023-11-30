package ti4.commands.ds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ATS extends DiscordantStarsSubcommandData {

    public ATS() {
        super(Constants.LANEFIR_ATS_COUNT, "Set commodity count on the ATS Armaments tech");
        addOptions(new OptionData(OptionType.INTEGER, "count", "Count").setRequired(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        int count = event.getOption("count").getAsInt() > 0 ? event.getOption("count").getAsInt() : 0;
        if (count > 0) {
            player.setAtsCount(count);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Set commodities count to " + count + " on the ATS Armaments tech");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Set commodities count to 0 on the ATS Armaments tech");
        }
    }
}
