package ti4.commands.game;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.commands.player.AddAllianceMember;
import ti4.helpers.Constants;
import ti4.helpers.RandomHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
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

        if (scenario.contains("ordinian")) {
            startOrdinianCodex1(game, event);
        }
        if (scenario.contains("liberation")) {
            startLiberationCodex4(game, event);
        }
        MessageHelper.replyToMessage(event, "Successfully started the scenario.");
    }

    public static void startOrdinianCodex1(Game game, GenericInteractionCreateEvent event) {
        game.setOrdinianC1Mode(true);
        var factions = List.of("arborec", "ghost", "muaat", "letnev", "nekro", "l1z1x");
        if (game.getRealPlayers().isEmpty()) {
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
        CommanderUnlockCheckService.checkPlayer(nekro, "nekro");

    }

    public static void startLiberationCodex4(Game game, GenericInteractionCreateEvent event) {
        game.setLiberationC4Mode(true);
        var factions = List.of("ghost", "xxcha", "sol", "naaz", "nekro", "nomad");
        if (game.getRealPlayers().isEmpty()) {
            AddTileListService.addTileListToMap(game, "{c41} 21 35 77 63 48 70 68 60 47 76 25 66 30 72 27 26 22 75 1 74 67 8 62 79 14 31 29 53 41 34 17 65 45 57 64 49", event);
        }
        List<Player> players = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            if (!player.isRealPlayer()) {
                players.add(player);
            }
        }
        for (String faction : factions) {
            if (players.isEmpty()) {
                MessageHelper.sendMessageToEventChannel(event, "You don't have six players, but I'll try my best anyway.");
                break;
            }
            if (game.getPlayerFromColorOrFaction(faction) == null) {
                int face = ThreadLocalRandom.current().nextInt(0, players.size());
                Tile tile = game.getTileFromPositionOrAlias(faction);
                if (faction.equalsIgnoreCase("ghost")) {
                    tile = game.getTileFromPositionOrAlias("creussgate");
                }
                boolean speaker = faction.equalsIgnoreCase("nekro");
                String color = players.get(face).getNextAvailableColour();
                switch (faction.toLowerCase())
                {
                    case "ghost":
                        color = RandomHelper.isOneInX(2) ? "ruby" : "bloodred";
                        break;
                    case "xxcha":
                        color = RandomHelper.isOneInX(2) ? "sunset" : "tropical";
                        break;
                    case "sol":
                        color = RandomHelper.isOneInX(2) ? "dawn" : "wasp";
                        break;
                    case "naaz":
                        color = RandomHelper.isOneInX(2) ? "lime" : "sherbet";
                        break;
                    case "nekro":
                        color = RandomHelper.isOneInX(2) ? "black" : "poison";
                        break;
                    case "nomad":
                        color = RandomHelper.isOneInX(2) ? "navy" : "glacier";
                        break;
                }
                if (tile != null) {
                    MiltyService.secondHalfOfPlayerSetup(players.get(face), game, color, faction, tile.getPosition(), event, speaker);
                    players.remove(face);
                }
            }
        }

        Player nekro = game.getPlayerFromColorOrFaction("nekro");
        Tile center = game.getTileByPosition("000");
        if (nekro != null) {
            nekro.addTech("nekroc4y");
            nekro.addTech("nekroc4r");
            nekro.setFleetCC(4);
            AddUnitService.addUnits(event, center, game, nekro.getColor(), "2 dread, 2 carriers, 4 ff, 4 inf o");
            nekro.refreshPlanet("ordinianc4");
        }

        Player ghost = game.getPlayerFromColorOrFaction("ghost");
        Tile gate = game.getTileByPosition("313");
        if (ghost != null) {
            AddUnitService.addUnits(event, gate, game, ghost.getColor(), "1 cruiser, 1 carrier, 2 inf");
            ghost.removeLeader("ghostagent");
            ghost.removeLeader("ghostcommander");
            ghost.removeLeader("ghosthero");
            ghost.addLeader("redcreussagent");
            ghost.addLeader("redcreusscommander");
            ghost.addLeader("redcreusshero");
            ghost.setFactionEmoji(FactionEmojis.RedCreuss.asEmoji().getFormatted());
        }
        List<String> allRelics = game.getAllRelics();

        Player nomad = game.getPlayerFromColorOrFaction("nomad");
        String relicID = "neuraloop";
        if (nomad != null) {
            allRelics.remove(relicID);
            nomad.addRelic(relicID);
            RelicModel relicModel = Mapper.getRelic(relicID);
            String message = nomad.getFactionEmoji() + " Drew Relic: " + relicModel.getName();
            MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), message, relicModel.getRepresentationEmbed(false, true));
        }

        Player naaz = game.getPlayerFromColorOrFaction("naaz");
        relicID = "circletofthevoid";
        if (naaz != null) {
            allRelics.remove(relicID);
            naaz.addRelic(relicID);
            RelicModel relicModel = Mapper.getRelic(relicID);
            String message = naaz.getFactionEmoji() + " Drew Relic: " + relicModel.getName();
            MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), message, relicModel.getRepresentationEmbed(false, true));
        }

        Player sol = game.getPlayerFromColorOrFaction("sol");
        Player xxcha = game.getPlayerFromColorOrFaction("xxcha");
        if (sol != null && xxcha != null) {
            AddAllianceMember.makeAlliancePartners(sol, xxcha, event, game);
            xxcha.addLeader("orlandohero");
        }
        game.addCustomPO("Liberate Ordinian", 1);
        if (game.getRealPlayers().size() == 6) {
            DrawSecretService.dealSOToAll(event, 2, game);
        }
        CommanderUnlockCheckService.checkPlayer(nekro, "nekro");

    }

}