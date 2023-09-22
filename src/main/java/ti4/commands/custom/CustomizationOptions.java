package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.game.GameSubcommandData;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class CustomizationOptions extends CustomSubcommandData{
    public CustomizationOptions() {
        super(Constants.CUSTOMIZATION, "Small Customization Options");
        addOptions(new OptionData(OptionType.STRING, Constants.TEXT_SIZE, "Small/medium/large, default small").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.STRAT_PINGS, "Set to YES if want strategy card follow reminders, FALSE to disable it").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.VERBOSITY, "Verbosity of bot output. Verbose/Average/Minimal  (Default = Verbose)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CC_N_PLASTIC_LIMIT, "Pings for exceeding limits. ON to turn on. OFF to turn off"));
        addOptions(new OptionData(OptionType.STRING, Constants.BOT_FACTION_REACTS, "Bot leaves your faction react on msgs. ON to turn on. OFF to turn off"));
        addOptions(new OptionData(OptionType.STRING, Constants.BOT_SHUSHING, "Bot shush reacts on msgs in actions. ON to turn on. OFF to turn off"));
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
        OptionMapping shushing = event.getOption(Constants.BOT_SHUSHING);
        if (shushing != null){
            String ccNP = shushing.getAsString();
            if ("ON".equalsIgnoreCase(ccNP)){
                activeGame.setShushing(true);
            } else if ("OFF".equalsIgnoreCase(ccNP)){
                activeGame.setShushing(false);
            }
        }

        OptionMapping largeText = event.getOption(Constants.TEXT_SIZE);
        if (largeText != null) {
            String large = largeText.getAsString();
            getActiveGame().setTextSize(large);
        }


        
        String verbosity = event.getOption(Constants.VERBOSITY, null, OptionMapping::getAsString);
        if (verbosity != null && Constants.VERBOSITY_OPTIONS.contains(verbosity)) activeGame.setOutputVerbosity(verbosity);
    }
}
