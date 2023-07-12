package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

public class FixSODeck extends CustomSubcommandData {
    public FixSODeck() {
        super(Constants.FIX_SO_DECK, "Put back into the deck any removed SOs");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
       activeMap.fixScrewedSOs();
        MapSaveLoadManager.saveMap(activeMap, event);
    }
}
