package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class Setup extends GameSubcommandData {
    public Setup() {
        super(Constants.SETUP, "Game Setup");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PLAYER_COUNT_FOR_MAP, "Specify player map size between 2 or 30. Default 6").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.VP_COUNT, "Specify game VP count").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_CUSTOM_NAME, "Add Custom description to game").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.TIGL_GAME, "Mark the game as TIGL"));
        addOptions(new OptionData(OptionType.STRING, Constants.COMMUNITY_MODE, "Set to YES if want Community Mode for map, FALSE to disable it").setRequired(false));
       // addOptions(new OptionData(OptionType.STRING, Constants.ALLIANCE_MODE, "Set to YES if want Alliance Mode for map, FALSE to disable it").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FOW_MODE, "YES if want FoW Mode for map, FALSE to disable it").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ABSOL_MODE, "True to switch out the PoK Agendas & Relics for Absol's "));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DISCORDANT_STARS_MODE, "True to add the Discordant Stars factions to the pool."));
        addOptions(new OptionData(OptionType.STRING, Constants.LARGE_TEXT, "Small/medium/large, default small").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.STRAT_PINGS, "Set to YES if want strategy card follow reminders, FALSE to disable it").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.AUTO_PING, "Hours between auto pings. Min 1. Enter 0 to turn off."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.BETA_TEST_MODE, "True to test new features that may not be released to all games yet."));
        addOptions(new OptionData(OptionType.STRING, Constants.VERBOSITY, "Verbosity of bot output. Verbose/Average/Minimal  (Default = Verbose)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CC_N_PLASTIC_LIMIT, "Pings for exceeding limits. ON to turn on. OFF to turn off"));
        addOptions(new OptionData(OptionType.STRING, Constants.BOT_FACTION_REACTS, "Bot leaves your faction react on msgs. ON to turn on. OFF to turn off"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        OptionMapping playerCount = event.getOption(Constants.PLAYER_COUNT_FOR_MAP);
        if (playerCount != null) {
            int count = playerCount.getAsInt();
            if (count < 2 || count > 30) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify between 2 or 30 players.");
            } else {
                activeGame.setPlayerCountForMap(count);
            }
        }

        OptionMapping vpOption = event.getOption(Constants.VP_COUNT);
        if (vpOption != null) {
            int count = vpOption.getAsInt();
            if (count < 1) {
                count = 1;
            } else if (count > 20) {
                count = 20;
            }
            activeGame.setVp(count);
        }

        DisplayType displayType = null;
        OptionMapping statsOption = event.getOption(Constants.DISPLAY_TYPE);
        if (statsOption != null) {
            String temp = statsOption.getAsString();
            if (temp.equals(DisplayType.all.getValue())) {
                displayType = DisplayType.all;
            }
            if (temp.equals(DisplayType.map.getValue())) {
                displayType = DisplayType.map;
            } else if (temp.equals(DisplayType.stats.getValue())) {
                displayType = DisplayType.stats;
            } else if ("none".equals(temp)) {
                activeGame.setDisplayTypeForced(null);
                return;
            }
        }
        OptionMapping communityOption = event.getOption(Constants.COMMUNITY_MODE);
        if (communityOption != null){
            String communityMode = communityOption.getAsString();
            if ("YES".equals(communityMode)){
                activeGame.setCommunityMode(true);
            } else if ("FALSE".equals(communityMode)){
                activeGame.setCommunityMode(false);
            }
        }

        OptionMapping fowOption = event.getOption(Constants.FOW_MODE);
        if (fowOption != null){
            String fowMode = fowOption.getAsString();
            if ("YES".equals(fowMode)){
                activeGame.setFoWMode(true);
            } else if ("FALSE".equals(fowMode)){
                activeGame.setFoWMode(false);
            }
        }

        OptionMapping stratPings = event.getOption(Constants.STRAT_PINGS);
        if (stratPings != null){
            String stratP = stratPings.getAsString();
            if ("YES".equalsIgnoreCase(stratP)){
                activeGame.setStratPings(true);
            } else if ("FALSE".equalsIgnoreCase(stratP)){
                activeGame.setStratPings(false);
            }
        }

        OptionMapping ccNPlastic = event.getOption(Constants.CC_N_PLASTIC_LIMIT);
        if (ccNPlastic != null){
            String ccNP = ccNPlastic.getAsString();
            if ("ON".equalsIgnoreCase(ccNP)){
                activeGame.setCCNPlasticLimit(true);
            } else if ("OFF".equalsIgnoreCase(ccNP)){
                activeGame.setCCNPlasticLimit(false);
            }
        }
        OptionMapping factReacts = event.getOption(Constants.BOT_FACTION_REACTS);
        if (factReacts != null){
            String ccNP = factReacts.getAsString();
            if ("ON".equalsIgnoreCase(ccNP)){
                activeGame.setBotFactionReactions(true);
            } else if ("OFF".equalsIgnoreCase(ccNP)){
                activeGame.setBotFactionReactions(false);
            }
        }


        OptionMapping homebrewSC = event.getOption(Constants.HOMEBREW_SC_MODE);
        if (homebrewSC != null){
            String ccNP = homebrewSC.getAsString();
            if ("ON".equalsIgnoreCase(ccNP)){
                activeGame.setHomeBrewSCMode(true);
            } else if ("OFF".equalsIgnoreCase(ccNP)){
                activeGame.setHomeBrewSCMode(false);
            }
        }

        OptionMapping largeText = event.getOption(Constants.LARGE_TEXT);
        if (largeText != null) {
            String large = largeText.getAsString();
            getActiveGame().setLargeText(large);
        }


        OptionMapping pingHours = event.getOption(Constants.AUTO_PING);
        if (pingHours != null) {
            int pinghrs = pingHours.getAsInt();
            if (pinghrs == 0) {
                activeGame.setAutoPing(false);
                activeGame.setAutoPingSpacer(pinghrs);
            } else {
                activeGame.setAutoPing(true);
                if (pinghrs < 1){
                    pinghrs = 1;
                }
                activeGame.setAutoPingSpacer(pinghrs);
            }
        }

        OptionMapping customOption = event.getOption(Constants.GAME_CUSTOM_NAME);
        if (customOption != null){
            String customName = customOption.getAsString();
            activeGame.setCustomName(customName);
        }

        Boolean isTIGLGame = event.getOption(Constants.TIGL_GAME, null, OptionMapping::getAsBoolean);
        if (isTIGLGame != null && isTIGLGame) {
            getActiveGame().setCompetitiveTIGLGame(true);
        }

        OptionMapping absolModeOption = event.getOption(Constants.ABSOL_MODE);
        if (absolModeOption != null) {
            getActiveGame().setAbsolMode(absolModeOption.getAsBoolean());
            getActiveGame().setAgendaDeckID("agendas_absol");
            getActiveGame().resetAgendas();
            getActiveGame().setRelicDeckID("relics_absol");
            getActiveGame().resetRelics();
        }

        OptionMapping discordantStarsOption = event.getOption(Constants.DISCORDANT_STARS_MODE);
        if (discordantStarsOption != null) {
            activeGame.setDiscordantStarsMode(discordantStarsOption.getAsBoolean());
        }

        if (displayType != null) {
            activeGame.setDisplayTypeForced(displayType);
        }

        Boolean betaTestMode = event.getOption(Constants.BETA_TEST_MODE, null, OptionMapping::getAsBoolean);
        if (betaTestMode != null) activeGame.setTestBetaFeaturesMode(betaTestMode);

        String verbosity = event.getOption(Constants.VERBOSITY, null, OptionMapping::getAsString);
        if (verbosity != null && Constants.VERBOSITY_OPTIONS.contains(verbosity)) activeGame.setOutputVerbosity(verbosity);
    }
}
