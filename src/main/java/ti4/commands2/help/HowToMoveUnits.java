package ti4.commands2.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.message.MessageHelper;

class HowToMoveUnits extends Subcommand {

    public HowToMoveUnits() {
        super(Constants.HOW_TO_MOVE_UNITS, "How to move units using the /move_units command");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.EXAMPLES_ONLY, "True to only show examples"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean examplesOnly = event.getOption(Constants.EXAMPLES_ONLY, false, OptionMapping::getAsBoolean);

        String message = "# So you want to add, remove, or move some units\n" +
            "# Use the [Modify Units] Button\n" +
            "> - The button will give you the options to either add or remove units from a system tile. Simply press the buttons to add/remove.\n" +
            "> - When adding units, you will be given the option to spend resources on them afterwards. If it's just a quick fix, just skip the spending part.\n" +
            "\n\n" +
            "If for whatever reason the Modify Units button does not suit your needs, you can go old school and use the `/move_units`, `/add_units`, or `/remove_units` commands:\n" +
            "# /move_units\n" +
            " - /move_units is simply equivalent to `/remove_units` followed by `/add_units` - it will remove units from one system, and add them to another. In that regard, the below logic can also be applied to `/remove_units` and `/add_units`\n" +
            "> - Typical /move_units command: `/move_units tile_name: meer unit_names: ws, 4 ff, 2 mech m tile_name_to: torkan unit_names_to: ws, 4 ff, 2 mech to`\n" +
            "## You'll need to input four things after /move_units:\n" +
            "### 1. __tile_name__\n> This is the name of the tile the units are in right now. It might be the same tile the units should end up in, but usually it won't be.\n" +
            "> - Examples include **310** (the tile number), **hacan** (the Hacan home tile), **meer** (the Arinam/Meer tile; **arinam** would work too), or you can pick from the suggestions as you start typing too.\n" +

            "### 2. __unit_names__\n> The units you want to move in the tile you just named.\n" +
            "> - Examples include **carrier, flagship, pds, spacedock, infantry,** etc. They are always one word, no spaces.\n" +
            ">  - All of these have aliases/abbreviations that you can use. Some common examples:\n" +
            ">   - Carrier = **cv**\n" +
            ">   - Cruiser = **cr**\n" +
            ">   - Destroyer = **dd**\n" +
            ">   - Dreadnought = **dn**\n" +
            ">   - Infantry = **inf**\n" +
            ">   - Fighter = **ff**\n" +
            ">   - Flagship = **fs**\n" +
            ">   - Warsun = **ws**\n" +
            ">   - Spacedock = **sd**\n" +
            "> - If you want to move multiples of a single unit, use **2 carriers** or **8 fighters** (or of course, **2 cv** or **8 ff**)\n" +
            "> - If you want to move units that are on **planets**, you must specify which planet the unit is on!\n" +
            ">  - E.g., `/move_units tile_name: meer unit_names: 2 cv, 4 ff, 2 inf` will move 2 carriers, 4 fighters, and 2 infantry *from the space area*.\n" +
            ">  - To move the 2 infantry from Arinam, you'll use /move_units tile_name: meer unit_names: 2 cv, 4 ff, 2 inf arinam to specify the planet the infantry are on.\n" +
            ">  - The bot just needs to know which planet it is within the tile; so for Arinam/Meer, **2 inf a** will be clear enough to specify arinam. For the Vegas you can either do **2 inf vegamajor** or can shorten to **2 inf vegama**; but **2 inf v** will be ambiguous.\n" +

            "### 3. __tile_name_to__\n> This is the name of the tile you want the units to end up in.\n" +

            "### 4. __unit_names_to__\n> The units you want to have end up in the tile you just named.\n" +
            "> - By default, units will end up in the space area of the tile. To place them on a planet, you'll need to specify the planet after the units.\n" +
            ">  - E.g., to move a war sun, 4 fighters, and 2 mechs from meer to torkan, you'd do:\n" +
            ">   - `/move_units tile_name: meer unit_names: ws, 4 ff, 2 mech m tile_name_to: torkan unit_names_to: ws, 4 ff, 2 mech to`\n" +
            ">    - Note that I had to put “to” for torkan instead of just “t”, since Tequ'ran is in the same tile.\n" +

            "## There are also four optional arguments that can sometimes be useful:\n" +
            "### 5. __faction_or_color__\n> By default, /move_units moves your own plastic. You can move other people's plastic (with their permission!) using this argument.\n" +
            "### 6. __cc_use__\n> By default, /move_units places a token from your tactical pool in the “to” tile.\n" +
            "> - If you do not want that to happen for any reason, you can use other options:\n" +
            ">  - “r” uses from your reinforcements (e.g., if you are retreating)\n" +
            ">  - “No” doesn't place a token (e.g., for Transit Diodes)\n" +
            "### 7. __prioritize_damaged__\n> I have never used this, but if you want to prioritize moving units that have not sustained damage, you can select that here (e.g., you have 4 dreads, 2 sustained, and you want to move the two undamaged ones only)\n" +
            "### 8. __no_mapgen__\n> 'True' to not produce a map image in the chat afterwards.\n" +

            "## Final notes:\n" +
            "- /move_units is really just doing /remove_units in the first tile and /add_units + /add_cc in the second tile, which has some interesting consequences.\n" +
            "- If you are using the ghosts commander with cruiser 2, the following would add all the fighters you'd gain (assuming this took you through a wormhole):\n" +
            " - `/move_units tile_name: creuss unit_names: 6 cr tile_name_to: 104 unit_names_to: 6cr, 6 ff`\n" +
            "# " + Emojis.BLT + "\n";

        String examples = """
            # Examples of the `/move_units` command:
            > - `/move_units tile_name: meer unit_names: ws, 4 ff, 2 mech m tile_name_to: torkan unit_names_to: ws, 4 ff, 1 mech to, 1 mech te` will remove 1 War Sun, 4 Fighters, 2 Mechs on Meer from the Arinam/Meer system and then add 1 War Sun, 4 Fighters, land 1 Mech on Tequran, and land 1 Mech on Torkan in the Tequran/Torkan system
            > - `/move_units tile_name: argent unit_names: 2 mech avar, 2 inf ylir tile_name_to: argent unit_names_to: mech valk, mech ylir, 2 inf avar` will remove 2 Mechs from Avar, 2 Infantry from Ylir and then add 1 Mech to Valk, 1 Mech to Ylir, and 2 Infantry to Avar
            """;

        if (examplesOnly) {
            MessageHelper.sendMessageToThread(event.getChannel(), "How to Move Units (Examples Only)", examples);
        } else {
            MessageHelper.sendMessageToThread(event.getChannel(), "How to Move Units", message + examples);
        }
    }
}
