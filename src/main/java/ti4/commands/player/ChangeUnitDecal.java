package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.uncategorized.ShowGame;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;

public class ChangeUnitDecal extends PlayerSubcommandData {
    public ChangeUnitDecal() {
        super(Constants.CHANGE_UNIT_DECAL, "Player Change Unit Decals");
        addOptions(new OptionData(OptionType.STRING, Constants.DECAL_SET, "Decals for units. Enter 'none' to remove current decals.").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
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

        String newDecalSet = event.getOption(Constants.DECAL_SET).getAsString().toLowerCase();
        if ("none".equals(newDecalSet)) {
            sendMessage("Decal Set removed: " + player.getDecalSet());
            player.setDecalSet(null);
            return;
        }
        if (!Mapper.isValidDecalSet(newDecalSet)) {
            sendMessage("Decal Set not valid: " + newDecalSet);
            player.setDecalSet(null);
            return;
        }


        player.setDecalSet(newDecalSet);
        sendMessage(player.getFactionEmojiOrColour() + " changed their decal set to " + newDecalSet);
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(activeGame, event);
        ShowGame.simpleShowGame(activeGame, event);
    }
}
