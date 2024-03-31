package ti4.commands.milty;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class SetupMilty extends MiltySubcommandData {

    private static final String setup = "setup";
    private static final String sliceCount= "slice_count";
    private static final String factionCount= "faction_count";
    private static final String playerCount= "player_count";
    private static final String minRes = "min_resources";
    private static final String minInf = "min_influence";
    private static final String anomTouch = "anomalies_can_touch";

    public SetupMilty() {
        super(setup, "Setup Milty Draft (Leave blank to see the defaults)");
        addOptions(new OptionData(OptionType.INTEGER, playerCount, "Player count").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, sliceCount, "Slice count").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, factionCount, "Faction count").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, minRes, "Minimum optimal resources").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, minInf, "Minimum optimal influence").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, anomTouch, "Anomalies can touch").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        if (!activeGame.isTestBetaFeaturesMode()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Milty Draft settings in this bot is incomplete.\nEnable access by running `/game setup beta_test_mode: true`\nMost folks use [this website](https://milty.shenanigans.be/) to do the Milty Draft and import the TTPG string with `/map add_tile_list`");
            return;
        }

        OptionMapping sliceOption = event.getOption(sliceCount);
        int sliceCount = activeGame.getPlayerCountForMap() + 2;
        if (sliceOption != null) {
            sliceCount = sliceOption.getAsInt();
        }
        if (sliceCount > 9) {
            String limit9slice = "Milty draft in this bot does not support more than 9 slices yet.";
            MessageHelper.sendMessageToChannel(event.getChannel(), limit9slice);
            return;
        }
        if (activeGame.getPlayers().values().size() > 8) {
            String limit8p = "Milty draft in this bot does not support more than 8 players yet.";
            MessageHelper.sendMessageToChannel(event.getChannel(), limit8p);
            return;
        }

        OptionMapping anomaliesCanTouchOption = event.getOption(anomTouch);
        if (anomaliesCanTouchOption != null) {
            //anomalies_can_touch = anomaliesCanTouchOption.getAsBoolean();
        }

    }
}
