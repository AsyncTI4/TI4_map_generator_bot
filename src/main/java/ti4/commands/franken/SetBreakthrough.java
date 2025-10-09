package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;

class SetBreakthrough extends GameStateSubcommand {

    public SetBreakthrough() {
        super(Constants.SET_BREAKTHROUGH, "Set the breakthrough you are using", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction Who's Breakthrough it is")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        String breakthrough = event.getOption(Constants.FACTION, null, OptionMapping::getAsString) + "bt";
        if (breakthrough.contains("keleres")) {
            breakthrough = "keleresbt";
        }
        player.setBreakthroughID(breakthrough);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getFactionEmojiOrColor() + " breakthrough set to: `"
                        + Mapper.getBreakthrough(breakthrough).getName() + "`");
    }
}
