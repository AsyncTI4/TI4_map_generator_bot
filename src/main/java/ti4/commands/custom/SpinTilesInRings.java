package ti4.commands.custom;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class SpinTilesInRings extends CustomSubcommandData {
    private static final String CW = "cw";
    private static final String CCW = "ccw";

    public SpinTilesInRings() {
        super(Constants.SPIN_TILES_IN_RINGS, "Rotate the map according to fin logic or give custom rotations");
        addOptions(new OptionData(OptionType.STRING, Constants.CUSTOM, "Custom rotation RingDirectionSteps 1cw1 2ccw2"));
        addOptions(new OptionData(OptionType.STRING, Constants.MESSAGE, "Flavour message to send to main channel after spins"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        if (event.getOption(Constants.CUSTOM) == null) {
          spinRings(activeGame);
        } else {
          spinRingsCustom(activeGame, event);
        }
    }

    public void spinRingsCustom(Game activeGame, SlashCommandInteractionEvent event) {
      String[] customSpins = event.getOption(Constants.CUSTOM).getAsString().toLowerCase().split(" ");
      List<Tile> tilesToSet = new ArrayList<>();
      for (String spinString : customSpins) {

        String[] spinSettings = spinString.split(":");
        if (spinSettings.length != 3) {
          MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Invalid spin settings: " + spinString);
          return;
        }

        int ring = Integer.parseInt(spinSettings[0]);
        String direction = spinSettings[1];
        int steps = Integer.parseInt(spinSettings[2]);
        if (ring <= 0 || (!CW.equals(direction) && !CCW.equals(direction)) || steps > ring * 6 - 1) {
          MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Invalid spin settings: " + spinString);
          return;
        }

        for (int x = 1; x < (ring * 6 + 1); x++) {
          Tile tile = activeGame.getTileByPosition(ring + (x < 10 ? "0" : "") + x);
          int pos;
          if (CW.equals(direction)) {
            if ((x + steps) > (ring * 6)) {
              pos = (x + steps) % (ring * 6);
            } else {
              pos = x + steps;
            }
          } else {
            if ((x - steps) < 1) {
              pos = (x - steps) + (ring * 6);
            } else {
              pos = x - steps;
            }
          }
          tile.setPosition(ring + (pos < 10 ? "0" : "") + pos);
          tilesToSet.add(tile);
        }
      }

      for (Tile tile : tilesToSet) {
        activeGame.setTile(tile);
      }
      activeGame.rebuildTilePositionAutoCompleteList();
      OptionMapping flavourMsg = event.getOption(Constants.MESSAGE);
      MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), flavourMsg != null ? flavourMsg.getAsString() : "Spun the rings");
    }

    //the original spin logic which does
    // - ring 1 cw one step
    // - ring 2 ccw two steps
    // - ring 3 cw three steps (except 6p map HS positions)
    public void spinRings(Game activeGame){
        List<Tile> tilesToSet = new ArrayList<>();
        //first ring
        for (int y = 1; y < 4; y++) {
            for (int x = 1; x < (y * 6 + 1); x++) {
                if (y == 3 && (x - 1) % 3 == 0) {
                    continue;
                }
                Tile tile;
                if (x < 10) {
                    tile = activeGame.getTileByPosition(y + "0" + x);
                } else {
                    tile = activeGame.getTileByPosition(y + "" + x);
                }
                if (y == 2) {
                    if ((x - y) < 1) {
                        tile.setPosition(y + "" + ((x - y) + (y * 6)));
                    } else {
                        if ((x - y) < 10) {
                            tile.setPosition(y + "0" + (x - y));
                        } else {
                            tile.setPosition(y + "" + (x - y));
                        }
                    }
                } else {
                    if ((x + y) > (y * 6)) {
                        tile.setPosition(y + "0" + ((x + y) % (y * 6)));
                    } else {
                        if ((x + y) < 10) {
                            tile.setPosition(y + "0" + (x + y));
                        } else {
                            tile.setPosition(y + "" + (x + y));
                        }
                    }
                }
                tilesToSet.add(tile);
            }
        }
        for (Tile tile : tilesToSet) {
            activeGame.setTile(tile);
        }
        activeGame.rebuildTilePositionAutoCompleteList();
        DisplayType displayType = DisplayType.map;
        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "Spun the rings");
    }
}
