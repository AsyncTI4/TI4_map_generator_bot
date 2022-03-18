package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class Setup extends PlayerSubcommandData{
    public Setup() {
        super(Constants.SETUP, "Player initialisation: Faction and Color");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction Name").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of units").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        @SuppressWarnings("ConstantConditions")
        String faction = AliasHandler.resolveFaction(event.getOption(Constants.FACTION).getAsString().toLowerCase());
        @SuppressWarnings("ConstantConditions")
        String color = event.getOption(Constants.COLOR).getAsString().toLowerCase();
        if (!Mapper.isColorValid(color)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Color not valid");
            return;
        }
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        player.setColor(color);
        player.setFaction(faction);
    }
}
