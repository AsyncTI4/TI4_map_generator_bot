package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;

public class CustomizationOptions extends CustomSubcommandData{
    public CustomizationOptions() {
        super(Constants.CUSTOMIZATION, "Small Customization Options");
        addOptions(new OptionData(OptionType.STRING, Constants.TEXT_SIZE, "tint/small/medium/large (default = medium)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.STRAT_PINGS, "Set to YES if want strategy card follow reminders, FALSE to disable it").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.VERBOSITY, "Verbosity of bot output. Verbose/Average/Minimal  (Default = Verbose)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CC_N_PLASTIC_LIMIT, "Pings for exceeding limits. ON to turn on. OFF to turn off"));
        addOptions(new OptionData(OptionType.STRING, Constants.BOT_FACTION_REACTS, "Bot leaves your faction react on msgs. ON to turn on. OFF to turn off"));
        addOptions(new OptionData(OptionType.STRING, Constants.SPIN_MODE, "Automatically spin inner three rings at status cleanup. ON to turn on. OFF to turn off"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_UNIT_TAGS, "Show faction unit tags on map images"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.NOMAD_COIN, "Replace tg emojis with nomad coin emojis"));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_SOURCE, "Swap player's owned units to units from another source").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();     

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
        OptionMapping shushing = event.getOption(Constants.SPIN_MODE);
        if (shushing != null){
            String ccNP = shushing.getAsString();
            if ("ON".equalsIgnoreCase(ccNP)){
                activeGame.setSpinMode(true);
            } else if ("OFF".equalsIgnoreCase(ccNP)){
                activeGame.setSpinMode(false);
            }
        }

        String textSize = event.getOption(Constants.TEXT_SIZE, null, OptionMapping::getAsString);
        if (textSize != null) getActiveGame().setTextSize(textSize);
        
        Boolean showUnitTags = event.getOption(Constants.SHOW_UNIT_TAGS, null, OptionMapping::getAsBoolean);
        if (showUnitTags != null) activeGame.setShowUnitTags(showUnitTags);

        Boolean nomad = event.getOption(Constants.NOMAD_COIN, null, OptionMapping::getAsBoolean);
        if (nomad != null) activeGame.setNomadCoin(nomad);
        
        String verbosity = event.getOption(Constants.VERBOSITY, null, OptionMapping::getAsString);
        if (verbosity != null && Constants.VERBOSITY_OPTIONS.contains(verbosity)) activeGame.setOutputVerbosity(verbosity);

        String unit_source = event.getOption(Constants.UNIT_SOURCE, null, OptionMapping::getAsString);
        if (unit_source != null && Mapper.getUnitSources().contains(unit_source)) activeGame.swapInVariantUnits(unit_source);
    }
}
