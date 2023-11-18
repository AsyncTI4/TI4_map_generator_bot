package ti4.commands.ds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.TileHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.TileModel;

public class AddOmenDie extends DiscordantStarsSubcommandData {

    public AddOmenDie() {
        super(Constants.ADD_OMEN_DIE, "Add a Omen Die");
        addOptions(new OptionData(OptionType.INTEGER, Constants.RESULT, "Number on the Omen Die").setRequired(true));
        // addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALL_ASYNC_TILES, "True to include all async blue back tiles in this list (not just PoK + DS). Default: false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        int dieResult = event.getOption(Constants.RESULT, 1, OptionMapping::getAsInt);
        ButtonHelperAbilities.addOmenDie(activeGame, dieResult);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Added an Omen Die with value "+dieResult);
    }
    
}
