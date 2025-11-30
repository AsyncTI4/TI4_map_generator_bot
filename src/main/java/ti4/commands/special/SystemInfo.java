package ti4.commands.special;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.image.TileGenerator;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;

class SystemInfo extends GameStateSubcommand {

    SystemInfo() {
        super(Constants.SYSTEM_INFO, "Info for system (all units)", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                OptionType.INTEGER,
                Constants.EXTRA_RINGS,
                "Show additional rings around the selected system for context (Max 2)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.JUST_UNITS, "True to just get unit information."));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_2, "System/Tile name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_3, "System/Tile name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_4, "System/Tile name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_5, "System/Tile name").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int context = 0;
        OptionMapping ringsMapping = event.getOption(Constants.EXTRA_RINGS);
        if (ringsMapping != null) {
            context = ringsMapping.getAsInt();
            int newContext = Math.min(context, 2);
            if (context < 0) newContext = 0;
            if (context == 333) newContext = 3;
            if (context == 444) newContext = 4;
            if (context == 555) newContext = 5;
            if (context == 666) newContext = 6;
            context = newContext;
        }
        boolean justUnits = false;
        Boolean just = event.getOption(Constants.JUST_UNITS, null, OptionMapping::getAsBoolean);
        if (just != null) justUnits = just;

        Game game = getGame();
        for (OptionMapping tileOption : event.getOptions()) {
            if (tileOption == null
                    || tileOption.getName().equals(Constants.EXTRA_RINGS)
                    || tileOption.getName().equals(Constants.JUST_UNITS)) {
                continue;
            }
            String tileID = tileOption.getAsString().toLowerCase();
            Tile tile = TileHelper.getTile(event, tileID, game);
            if (tile == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Tile " + tileOption.getAsString() + " not found");
                continue;
            }
            secondHalfOfSystemInfo(game, justUnits, tile, getPlayer(), event.getChannel(), event, context);
        }
    }

    public static void secondHalfOfSystemInfo(
            Game game,
            boolean justUnits,
            Tile tile,
            Player ogPlayer,
            MessageChannelUnion channel,
            GenericInteractionCreateEvent event,
            int context) {

        if (game.isFowMode()
                && !ogPlayer.isGM()
                && !FoWHelper.getTilePositionsToShow(game, ogPlayer).contains(tile.getPosition())) {
            MessageHelper.sendMessageToChannel(channel, "You have no visibility to " + tile.getPosition() + ".");
            return;
        }

        FileUpload systemWithContext =
                new TileGenerator(game, event, null, context, tile.getPosition()).createFileUpload();
        MessageHelper.sendMessageToChannel(
                channel, ButtonHelper.getTileSummaryMessage(game, justUnits, tile, ogPlayer, event));
        MessageHelper.sendMessageWithFile(channel, systemWithContext, tile.getRepresentationForButtons(), false);
        if (game.isFowMode()) {
            return;
        }
        if (!justUnits) {
            for (Player player : game.getRealPlayers()) {
                if (!FoWHelper.playerHasUnitsInSystem(player, tile)) {
                    continue;
                }
                List<Player> players = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, game, tile);
                if (!players.isEmpty()
                        && !player.getAllianceMembers().contains(players.get(0).getFaction())
                        && FoWHelper.playerHasShipsInSystem(player, tile)) {
                    Player player2 = players.get(0);
                    if (player2 == player) {
                        player2 = players.get(1);
                    }
                    List<Button> buttons = StartCombatService.getGeneralCombatButtons(
                            game, tile.getPosition(), player, player2, "space");
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), " ", buttons);
                    return;
                } else {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder instanceof Planet) {
                            if (ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, unitHolder.getName())
                                            .size()
                                    > 1) {
                                List<Player> listP =
                                        ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, unitHolder.getName());
                                List<Button> buttons = StartCombatService.getGeneralCombatButtons(
                                        game, tile.getPosition(), listP.get(0), listP.get(1), "ground");
                                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), " ", buttons);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}
