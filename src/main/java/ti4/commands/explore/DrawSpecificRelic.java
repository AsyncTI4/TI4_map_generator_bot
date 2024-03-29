package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.model.RelicModel;

import java.util.List;

public class DrawSpecificRelic extends GenericRelicAction {

    public DrawSpecificRelic() {
        super(Constants.RELIC_DRAW_SPECIFIC, "Draw a specific relic", true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to exhaust").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do edit").setRequired(false));
    }

    @Override
    public void doAction(Player player, SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.RELIC);
        if (option == null) {
            sendMessage("Specify relic");
            return;
        }
        String relicId = option.getAsString();
        List<String> allRelics = getActiveGame().getAllRelics();
        if (allRelics.contains(relicId) || Constants.ENIGMATIC_DEVICE.equals(relicId)) {
            if (!Constants.ENIGMATIC_DEVICE.equals(relicId)) {
                allRelics.remove(relicId);
            }
            player.addRelic(relicId);
            RelicModel relicData = Mapper.getRelic(relicId);
            String relicString = "Relic: " + relicData.getName() + " - " + relicData.getText();
            sendMessage(relicString);
        } else {
            sendMessage("Invalid relic or relic not present in deck");
        }
    }
}
