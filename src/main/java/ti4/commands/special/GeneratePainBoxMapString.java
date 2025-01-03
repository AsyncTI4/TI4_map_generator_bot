package ti4.commands.special;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.TileModel;
import ti4.service.map.AddTileService;
import ti4.service.map.AddTileService.RandomOption;

class GeneratePainBoxMapString extends GameStateSubcommand {

  /*
    Generates a map string for Eronous PainBox type map where no tiles are adjacent to each other.
  */  
  public GeneratePainBoxMapString() {
        super(Constants.GENERATE_PAINBOX_MAP, "Generate random map string for Eronous Pain Box", false, false);
        addOption(OptionType.INTEGER, Constants.BLUE_TILES, "How many random Blue tiles", true);
        addOption(OptionType.INTEGER, Constants.RED_TILES, "How many random Red tiles", true);
        addOption(OptionType.INTEGER, Constants.HOME_SYSTEMS, "How many Home Systems (default: 6)", false);
        addOption(OptionType.INTEGER, Constants.SUPERNOVAS, "How many Supernovas", false);
        addOption(OptionType.STRING, Constants.TILE_LIST, "Also include these tiles", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        int blueTiles = event.getOption(Constants.BLUE_TILES, 0, OptionMapping::getAsInt);
        int redTiles = event.getOption(Constants.RED_TILES, 0, OptionMapping::getAsInt);
        int hsTiles = event.getOption(Constants.HOME_SYSTEMS, 6, OptionMapping::getAsInt);
        int supernovas = event.getOption(Constants.SUPERNOVAS, 0, OptionMapping::getAsInt);
        String fixedTiles = event.getOption(Constants.TILE_LIST, "", OptionMapping::getAsString);
        List<String> fixedTilesList = fixedTiles.isEmpty() ? new ArrayList<>() : Arrays.asList(fixedTiles.split("[,\\s]+")).stream().map(String::trim).toList();

        List<String> tileList = new ArrayList<>();
        tileList.add("18");
        tileList.addAll(Collections.nCopies(blueTiles, RandomOption.B.toString()));
        tileList.addAll(Collections.nCopies(redTiles, RandomOption.R.toString()));
        tileList.addAll(Collections.nCopies(hsTiles, "0")); //Green tile back
        tileList.addAll(fixedTilesList);

        if (supernovas > 0) {
            List<TileModel> allSupernovas = 
                AddTileService.availableTiles(AddTileService.getSources(game, true), RandomOption.R, new HashSet<>(), new ArrayList<>())
                    .stream().filter(s -> s.isSupernova()).collect(Collectors.toList());
            for (int i = 0; i < supernovas; i++) {
                Collections.shuffle(allSupernovas);
                TileModel sn = allSupernovas.getFirst();
                tileList.add(sn.getId());
                if (!sn.isEmpty()) allSupernovas.remove(sn);
            }
        }

        //Populate the map
        int totalNumberOfTiles = tileList.size();
        Collections.shuffle(tileList);
        List<String> map = new ArrayList<>();
        map.add(tileList.removeFirst()); //000
        int rings = 0;
        while (!tileList.isEmpty()) {
            rings++;

            if (rings > 16) {
                MessageHelper.replyToMessage(event, "Amount of rings needed exceeds max allowed (16).");
                return;
            }

            for (int i = 1; i <= (rings * 6); i++) {
                if (rings % 2 != 0 || i % 2 == 0 || tileList.isEmpty()) {
                    map.add("-1");
                } else {
                    map.add(tileList.removeFirst());
                }
            }
        }

        String mapString = "{" + map.removeFirst() + "} " + String.join(" ", map);
        StringBuffer sb = new StringBuffer();
        sb.append("Generated Eronous PainBox with ").append(totalNumberOfTiles).append(" tiles ");
        sb.append("(MR + ").append(blueTiles).append(" blues + ");
        sb.append(redTiles).append(" reds");
        if (hsTiles > 0) sb.append(" + ").append(hsTiles).append(" hs placeholders");
        if (supernovas > 0) sb.append(" + ").append(supernovas).append(" supernovas");
        if (fixedTilesList.size() > 0) sb.append(" + ").append(fixedTilesList.size()).append(" fixed tiles");
        sb.append(") into ").append(rings).append(" rings.\nUse `/map add_tile_list_random` to insert the map string:");

        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        MessageHelper.sendMessageToChannel(event.getChannel(), "`\n" + mapString + "\n`");
    }
}
