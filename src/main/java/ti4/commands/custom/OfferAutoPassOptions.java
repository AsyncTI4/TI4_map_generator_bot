package ti4.commands.custom;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.generator.GenerateMap;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class OfferAutoPassOptions extends CustomSubcommandData {
    public OfferAutoPassOptions() {
        super(Constants.OFFER_AUTOPASS_OPTIONS, "Offer auto pass on sabos to every player in the game");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        ButtonHelper.offerSetAutoPassOnSaboButtons(activeGame);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Offered options");
    }
}
