package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;

public class CustomizationOptions extends CustomSubcommandData {
    public CustomizationOptions() {
        super(Constants.CUSTOMIZATION, "Small Customization Options");
        addOptions(new OptionData(OptionType.STRING, Constants.TEXT_SIZE, "tint/small/medium/large (default = medium)")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.STRAT_PINGS,
                "Set to YES if want strategy card follow reminders, FALSE to disable it").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_FULL_COMPONENT_TEXT,
                "Show full text of components when using/exhausting"));
        addOptions(new OptionData(OptionType.STRING, Constants.VERBOSITY,
                "Verbosity of bot output. Verbose/Average/Minimal  (Default = Verbose)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CC_N_PLASTIC_LIMIT,
                "Pings for exceeding limits. ON to turn on. OFF to turn off"));
        addOptions(new OptionData(OptionType.STRING, Constants.BOT_FACTION_REACTS,
                "Bot leaves your faction react on msgs. ON to turn on. OFF to turn off"));
        addOptions(new OptionData(OptionType.STRING, Constants.SPIN_MODE,
                "Automatically spin inner three rings at status cleanup. ON to turn on. OFF to turn off"));
        addOptions(
                new OptionData(OptionType.BOOLEAN, Constants.SHOW_UNIT_TAGS, "Show faction unit tags on map images"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.LIGHT_FOG_MODE, "Retain sight on formerly seen tiles"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.RED_TAPE_MODE,
                "Reveal all objectives and diplo gets the power to pre-reveal"));
        addOptions(
                new OptionData(OptionType.BOOLEAN, Constants.NOMAD_COIN, "Replace tg emojis with nomad coin emojis"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.QUEUE_SO, "Queue SO Discards"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_BUBBLES,
                "Show the bubbles around anti-bombardment planets"));
        addOptions(
                new OptionData(OptionType.BOOLEAN, Constants.SHOW_GEARS, "Show the production capacity in a system"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.HOMEBREW_MODE, "Mark the game as homebrew"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INJECT_RULES_LINKS,
                "Have the bot inject helpful links to rules within it's output"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.UNDO_BUTTON, "Offer Undo Button"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FAST_SC_FOLLOW,
                "Consider People To Pass on SCs if they dont respond with 24hrs"));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_SOURCE,
                "Swap player's owned units to units from another source").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        OptionMapping stratPings = event.getOption(Constants.STRAT_PINGS);
        if (stratPings != null) {
            String stratP = stratPings.getAsString();
            if ("YES".equalsIgnoreCase(stratP)) {
                activeGame.setStratPings(true);
            } else if ("FALSE".equalsIgnoreCase(stratP)) {
                activeGame.setStratPings(false);
            }
        }

        OptionMapping ccNPlastic = event.getOption(Constants.CC_N_PLASTIC_LIMIT);
        if (ccNPlastic != null) {
            String ccNP = ccNPlastic.getAsString();
            if ("ON".equalsIgnoreCase(ccNP)) {
                activeGame.setCCNPlasticLimit(true);
            } else if ("OFF".equalsIgnoreCase(ccNP)) {
                activeGame.setCCNPlasticLimit(false);
            }
        }
        OptionMapping factReacts = event.getOption(Constants.BOT_FACTION_REACTS);
        if (factReacts != null) {
            String ccNP = factReacts.getAsString();
            if ("ON".equalsIgnoreCase(ccNP)) {
                activeGame.setBotFactionReactions(true);
            } else if ("OFF".equalsIgnoreCase(ccNP)) {
                activeGame.setBotFactionReactions(false);
            }
        }
        OptionMapping shushing = event.getOption(Constants.SPIN_MODE);
        if (shushing != null) {
            String ccNP = shushing.getAsString();
            if ("ON".equalsIgnoreCase(ccNP)) {
                activeGame.setSpinMode(true);
            } else if ("OFF".equalsIgnoreCase(ccNP)) {
                activeGame.setSpinMode(false);
            }
        }

        String textSize = event.getOption(Constants.TEXT_SIZE, null, OptionMapping::getAsString);
        if (textSize != null)
            getActiveGame().setTextSize(textSize);

        Boolean showFullTextComponents = event.getOption(Constants.SHOW_FULL_COMPONENT_TEXT, null,
                OptionMapping::getAsBoolean);
        if (showFullTextComponents != null)
            activeGame.setShowFullComponentTextEmbeds(showFullTextComponents);

        Boolean showUnitTags = event.getOption(Constants.SHOW_UNIT_TAGS, null, OptionMapping::getAsBoolean);
        if (showUnitTags != null)
            activeGame.setShowUnitTags(showUnitTags);

        Boolean nomad = event.getOption(Constants.NOMAD_COIN, null, OptionMapping::getAsBoolean);
        if (nomad != null)
            activeGame.setNomadCoin(nomad);

        Boolean light = event.getOption(Constants.LIGHT_FOG_MODE, null, OptionMapping::getAsBoolean);
        if (light != null)
            activeGame.setLightFogMode(light);
        Boolean red = event.getOption(Constants.RED_TAPE_MODE, null, OptionMapping::getAsBoolean);
        if (red != null)
            activeGame.setRedTapeMode(red);

        Boolean showB = event.getOption(Constants.SHOW_BUBBLES, null, OptionMapping::getAsBoolean);
        if (showB != null)
            activeGame.setShowBubbles(showB);

        Boolean showG = event.getOption(Constants.SHOW_GEARS, null, OptionMapping::getAsBoolean);
        if (showG != null)
            activeGame.setShowGears(showG);

        Boolean homebrew = event.getOption(Constants.HOMEBREW_MODE, null, OptionMapping::getAsBoolean);
        if (homebrew != null)
            activeGame.setHomeBrew(homebrew);

        Boolean injectRules = event.getOption(Constants.INJECT_RULES_LINKS, null, OptionMapping::getAsBoolean);
        if (injectRules != null)
            activeGame.setInjectRulesLinks(injectRules);

        Boolean queueSO = event.getOption(Constants.QUEUE_SO, null, OptionMapping::getAsBoolean);
        if (queueSO != null) {
            activeGame.setQueueSO(queueSO);
            if (!queueSO) {
                String key = "factionsThatAreNotDiscardingSOs";
                String key2 = "queueToDrawSOs";
                String key3 = "potentialBlockers";
                activeGame.setStoredValue(key, "");
                activeGame.setStoredValue(key2, "");
                activeGame.setStoredValue(key3, "");
            }
        }

        Boolean undo = event.getOption(Constants.UNDO_BUTTON, null, OptionMapping::getAsBoolean);
        if (undo != null)
            activeGame.setUndoButton(undo);

        Boolean fast = event.getOption(Constants.FAST_SC_FOLLOW, null, OptionMapping::getAsBoolean);
        if (fast != null)
            activeGame.setFastSCFollowMode(fast);

        String verbosity = event.getOption(Constants.VERBOSITY, null, OptionMapping::getAsString);
        if (verbosity != null && Constants.VERBOSITY_OPTIONS.contains(verbosity))
            activeGame.setOutputVerbosity(verbosity);

        String unitSource = event.getOption(Constants.UNIT_SOURCE, null, OptionMapping::getAsString);
        if (unitSource != null && Mapper.getUnitSources().contains(unitSource))
            activeGame.swapInVariantUnits(unitSource);
    }
}
