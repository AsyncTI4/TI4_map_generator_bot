package ti4.commands.game;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.map.AddTileListService;
import ti4.service.milty.MiltyService;
import ti4.service.objectives.DrawSecretService;
import ti4.service.unit.AddUnitService;

public class StartScenario extends GameStateSubcommand {

    public StartScenario() {
        super(Constants.START_SCENARIO, "Start a codex scanerio", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.SCENARIO, "Scenario name").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String scenario = event.getOption(Constants.SCENARIO).getAsString();

        if (scenario != null && scenario.contains("ordinian")) {
            startOrdinianCodex1(game, event);
        }
        MessageHelper.replyToMessage(event, "Successfully started the scenario");
    }

    public static void startOrdinianCodex1(Game game, GenericInteractionCreateEvent event) {
        game.setOrdinianC1Mode(true);
        var factions = List.of("arborec", "ghost", "muaat", "letnev", "nekro", "l1z1x");
        if (game.getRealPlayers().size() == 0) {
            AddTileListService.addTileListToMap(game, "{42} 32 43 25 47 33 36 19 37 28 21 48 29 27 24 38 30 40 22 10 50 26 4 49 45 17 35 31 5 44 39 8 41 34 6 20 23", event);
        }
        List<Player> players = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            if (!player.isRealPlayer()) {
                players.add(player);
            }
        }
        for (String faction : factions) {
            if (game.getPlayerFromColorOrFaction(faction) == null) {
                int face = ThreadLocalRandom.current().nextInt(0, players.size());
                Tile tile = game.getTileFromPositionOrAlias(faction);
                if (faction.equalsIgnoreCase("ghost")) {
                    tile = game.getTileFromPositionOrAlias("creussgate");
                }
                boolean speakerAlreadyExist = game.getSpeaker() != null;
                boolean speaker = false;
                if (!speakerAlreadyExist) {
                    int chance = ThreadLocalRandom.current().nextInt(0, players.size());
                    speaker = chance == face;
                }
                if (tile != null) {
                    MiltyService.secondHalfOfPlayerSetup(players.get(face), game, players.get(face).getNextAvailableColour(), faction, tile.getPosition(), event, speaker);
                    players.remove(face);
                }
            }
        }

        if (game.getRealPlayers().size() == 6) {
            DrawSecretService.dealSOToAll(event, 2, game);
        }
        Player nekro = game.getPlayerFromColorOrFaction("nekro");
        Tile center = game.getTileByPosition("000");
        if (nekro != null) {
            nekro.addTech("ah");
            nekro.addTech("swa2");
            AddUnitService.addUnits(event, center, game, nekro.getColor(), "2 dread, fs, 2 ff");
        }
        game.addCustomPO("Coatl Control", 1);
        game.addCustomPO("Coatl HS", 1);
        center.addToken("token_custc1.png", "space");

    }

}