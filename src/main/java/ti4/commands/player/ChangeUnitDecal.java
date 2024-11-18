package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.UnitDecalService;

class ChangeUnitDecal extends GameStateSubcommand {

    public ChangeUnitDecal() {
        super(Constants.CHANGE_UNIT_DECAL, "Player Change Unit Decals", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.DECAL_SET, "Decals for units. Enter 'none' to remove current decals.").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();

        String newDecalSet = event.getOption(Constants.DECAL_SET).getAsString().toLowerCase();
        if ("none".equals(newDecalSet)) {
            MessageHelper.sendMessageToEventChannel(event, "Decal Set removed: " + player.getDecalSet());
            player.setDecalSet(null);
            return;
        }
        if (!Mapper.isValidDecalSet(newDecalSet)) {
            MessageHelper.sendMessageToEventChannel(event, "Decal Set not valid: " + newDecalSet);
            player.setDecalSet(null);
            return;
        }
        if (!UnitDecalService.userMayUseDecal(player.getUserID(), newDecalSet)) {
            MessageHelper.sendMessageToEventChannel(event, "This decal set may only be used by specific players.");
            return;
        }

        player.setDecalSet(newDecalSet);
        MessageHelper.sendMessageToEventChannel(event, player.getFactionEmojiOrColor() + " changed their decal set to " + newDecalSet);
    }
}
