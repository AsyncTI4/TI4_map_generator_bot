package ti4.commands.capture;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

abstract class CaptureReleaseUnits extends CaptureSubcommandData {

    public CaptureReleaseUnits(String id, String description) {
        super(id, description);
        options();
    }

    protected void options() {
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Comma separated list of '{count} unit' Eg. 2 infantry, carrier, 2 fighter, mech").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        GameManager gameManager = GameManager.getInstance();
        if (!gameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return;
        }
        Game game = gameManager.getUserActiveGame(userID);
        String color = Helper.getColor(game, event);
        if (!Mapper.isValidColor(color)) {
            MessageHelper.replyToMessage(event, "Color/Faction not valid");
            return;
        }

        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event.getMember(), getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player could not be found");
            return;
        }

        Tile tile = player.getNomboxTile();
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player nombox could not be found");
            return;
        }
        subExecute(event, tile);
    }

    public String getPlayerColor(GenericInteractionCreateEvent event) {
        Player player = getActiveGame().getPlayer(getUser().getId());
        return player.getColor();
    }

    protected abstract void subExecute(SlashCommandInteractionEvent event, Tile tile);
}
