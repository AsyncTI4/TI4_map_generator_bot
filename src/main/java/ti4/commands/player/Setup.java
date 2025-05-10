package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.milty.MiltyService;

class Setup extends GameStateSubcommand {

    public Setup() {
        super(Constants.SETUP, "Player initialisation: Faction and Color", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction Name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.HS_TILE_POSITION, "Home system tile position (or equivalent e.g. Creuss Gate)").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of units").setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SPEAKER, "True to set player as speaker."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String faction = event.getOption(Constants.FACTION, null, OptionMapping::getAsString);
        if (faction != null) {
            faction = StringUtils.substringBefore(faction.toLowerCase().replace("the ", ""), " ");
        }

        faction = AliasHandler.resolveFaction(faction);
        if (!Mapper.isValidFaction(faction)) {
            MessageHelper.sendMessageToEventChannel(event, "Faction `" + faction + "` is not valid. Valid options are: " + Mapper.getFactionIDs());
            return;
        }

        Player player = getPlayer();

        String color = AliasHandler.resolveColor(event.getOption(Constants.COLOR, player.getNextAvailableColour(), OptionMapping::getAsString).toLowerCase());
        if (!Mapper.isValidColor(color)) {
            MessageHelper.sendMessageToEventChannel(event, "Color `" + color + "` is not valid. Options are: " + Mapper.getColors());
            return;
        }

        // SPEAKER
        boolean setSpeaker = event.getOption(Constants.SPEAKER, false, OptionMapping::getAsBoolean);
        String positionHS = StringUtils.substringBefore(event.getOption(Constants.HS_TILE_POSITION, "", OptionMapping::getAsString), " "); // Substring to grab "305" from "305 Moll Primus (Mentak)" autocomplete
        MiltyService.secondHalfOfPlayerSetup(player, game, color, faction, positionHS, event, setSpeaker);
    }
}
