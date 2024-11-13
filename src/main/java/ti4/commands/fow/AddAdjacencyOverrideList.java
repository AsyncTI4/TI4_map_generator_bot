package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class AddAdjacencyOverrideList extends GameStateSubcommand {

  public AddAdjacencyOverrideList() {
    super(Constants.ADD_ADJACENCY_OVERRIDE_LIST, "Add Custom Adjacent Tiles as a List.", true, true);
    addOptions(new OptionData(OptionType.STRING, Constants.ADJACENCY_OVERRIDES_LIST, "Primary:Direction:Secondary 101:nw:202")
      .setRequired(true));
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    OptionMapping adjacencyList = event.getOption(Constants.ADJACENCY_OVERRIDES_LIST);

    String[] adjacencyListOptions = adjacencyList.getAsString().toLowerCase().split(" ");
    for (String adjacencyOption : adjacencyListOptions) {
      String[] adjacencyTile = adjacencyOption.split(":");
      if (adjacencyTile.length < 3) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid Adjacency Settings");
        return;
      }

      String primaryTile = adjacencyTile[0];

      int direction;
      switch (adjacencyTile[1]) {
        case "n" -> direction = 0;
        case "ne" -> direction = 1;
        case "se" -> direction = 2;
        case "s" -> direction = 3;
        case "sw" -> direction = 4;
        case "nw" -> direction = 5;
        default -> direction = -1;
      }

      String secondaryTile = adjacencyTile[2];

      if (primaryTile.isBlank() || secondaryTile.isBlank() || direction == -1) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid Adjacency Settings");
        return;
      }

      getGame().addAdjacentTileOverride(primaryTile, direction, secondaryTile);
    }
  }
}
