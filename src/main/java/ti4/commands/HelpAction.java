package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class HelpAction implements Command {

    @Override
    public String getActionID() {
        return Constants.HELP;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "TEST ACTION EXECUTED");
    }
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Help Action")

        );
    }

    private String getHelpText() {

        return "Commands:\n" +
                "First you need to create map and set active map you modifying.\n" +
                "To create map use:\n" +
                "/create_map mapName\n" +
                "To set active map use:\n" +
                "/set_map mapName\n" +
                "\n" +
                "For easy of setup suggest first command after map creation use\n" +
                "/add_tile setup 0\n" +
                "This adds a template with positions\n" +
                "\n" +
                "\n" +
                "Now additional commands:\n" +
                "/add_tile tileName positionOnMap\n" +
                "(We using somno template positions on map. from setup template and you see positions.\n" +
                "Also we support TopLeft, TopRight, BottomLeft and BottomRight positions coresponding to TL, TR, BL, BR\n" +
                "Mecatol position is MR)\n" +
                "/add_tile_list tileList <- tile list is code from map Generators like TTS uses, example: https://ti4-map-generator.derekpeterson.ca/\n" +
                "You can use also tileID, so liek TI4 marks tiles 01 is Jord for example\n" +
                "/list_tiles <- lists all tiles, use starting number id to add to map\n" +
                "/remove_tile tileName or position\n" +
                "/list_maps <- lists all available maps that are created\n" +
                "/delete_map <-deletes your map, only can delete the map you created\n" +
                "/show_map mapName <- displays the map\n" +
                "/list_units <- lists all possible units\n" +
                "/add_cc color tile or position\n" +
                "/remove_cc color tile or position\n" +
                "/remove_all_cc tile or position\n" +
                "/add_control color tile planet <- need to specify tile and planet, we working to improve it\n" +
                "/remove_control color tile planet\n" +
                "and most interesting is \n" +
                "/add_units color tile unitList\n" +
                "/remove_units color tile unitList\n" +
                "/remove_all_units color tile\n" +
                "\n" +
                "unitList examples and explanation. If dont specify planet, all units go into space\n" +
                "Listing only unit list, so add into command that you want\n" +
                "dn <- sinle dread into space\n" +
                "dread <- same sinle dread into space\n" +
                "3 dn <- 3 dreads added into space\n" +
                "3 dn, 2 destroyers <- 3dread and 2 dd added into space\n" +
                "3 dn, 2 dd, gf quann <- 3 dread and 2dd added into space, 1 gf added onto planet quann\n" +
                "ws, 6 ff, sd quann, 3 inf quann, mech quann <- warsun and 6 fighters added into space, spacedock, 3 infantry and mech added into quann\n";
    }
}
