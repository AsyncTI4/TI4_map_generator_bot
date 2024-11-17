package ti4.commands2.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.SpinRingsHelper;
import ti4.map.Game;

class SpinTilesInRings extends GameStateSubcommand {

  public SpinTilesInRings() {
    super(Constants.SPIN_TILES_IN_RINGS, "Rotate the map according to fin logic or give custom rotations", true, true);
    addOptions(new OptionData(OptionType.STRING, Constants.CUSTOM, "Custom rotation Ring:Direction:Steps 1:cw:1 2:ccw:2"));
    addOptions(new OptionData(OptionType.STRING, Constants.MESSAGE, "Flavour message to send to main channel after spins"));
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    Game game = getGame();
    if (event.getOption(Constants.CUSTOM) == null) {
      SpinRingsHelper.spinRings(game);
    } else {
      SpinRingsHelper.spinRingsCustom(game, event);
    }
  }
}
