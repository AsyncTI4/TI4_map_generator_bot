package ti4.commands.tf;

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
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

class SetupStartingFleet extends GameStateSubcommand {

    SetupStartingFleet() {
        super(Constants.SETUP_STARTING_FLEET, "Adding starting fleet", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Fleets Faction Name")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.POSITION,
                        "Place where you want to add the fleet. E.g. '305' or '305 Moll Primus (Mentak)'")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Player who is being set up")
                .setAutoComplete(true));
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
            MessageHelper.sendMessageToEventChannel(
                    event, "Faction `" + faction + "` is not valid. Valid options are: " + Mapper.getFactionIDs());
            return;
        }

        Player player = getPlayer();

        if (!player.isRealPlayer() && !"neutral".equals(player.getFaction())) {
            MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + " is not a real player.");
            return;
        }

        String positionHS = StringUtils.substringBefore(
                event.getOption(Constants.POSITION, "", OptionMapping::getAsString),
                " "); // Substring to grab "305" from "305 Moll Primus (Mentak)" autocomplete
        Tile tile = game.getTileByPosition(positionHS);
        if (tile != null) {
            String unitList = Mapper.getFaction(faction).getStartingFleet();
            AddUnitService.addUnitsToDefaultLocations(event, tile, game, player.getColor(), unitList);
        } else {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "Position `" + positionHS
                            + "` is not valid. Please select a valid position from the autocomplete.");
        }
    }
}
