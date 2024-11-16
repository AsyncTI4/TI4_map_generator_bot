package ti4.commands2.capture;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.units.AddUnits;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Tile;

class AddCaptureUnits extends CaptureUnitsCommand {

    public AddCaptureUnits() {
        super(Constants.ADD_UNITS, "Capture units");
    }

    // TODO: this feels super hacky and would may be be better re-written...
    @Override
    protected void subExecute(SlashCommandInteractionEvent event, Tile tile) {
        AddUnits addUnits = new AddUnits() {
            @Override
            public Tile getTileObject(SlashCommandInteractionEvent event, String tileID, Game game) {
                return tile;
            }

            @Override
            private String recheckColorForUnit(String unit, String color, GenericInteractionCreateEvent event) {
                if (unit.contains("ff") || unit.contains("gf")) {
                    return getPlayerColor(event);
                }
                return color;
            }
        };
        addUnits.execute(event);
    }
}
