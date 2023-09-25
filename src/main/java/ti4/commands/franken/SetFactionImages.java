package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;

public class SetFactionImages extends FrankenSubcommandData {

    public SetFactionImages() {
        super(Constants.SET_FACTION_IMAGES, "Set franken faction images to use");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_EMOJI, "Emoji to use"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_IMAGE_URL, "Image to use"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.RESET, "Reset images to default"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        if (event.getOption(Constants.RESET) != null && event.getOption(Constants.RESET).getAsBoolean()) {
            player.setFactionEmoji(null);
            return;
        }

        String factionEmoji = event.getOption(Constants.FACTION_EMOJI, null, OptionMapping::getAsString);
        if (factionEmoji != null) {
            player.setFactionEmoji(factionEmoji);
        }

        String factionImage = event.getOption(Constants.FACTION_IMAGE_URL, null, OptionMapping::getAsString);
        if (factionImage != null) {
            // player.setFactionImage(factionImage);
        }

    }
    
}
