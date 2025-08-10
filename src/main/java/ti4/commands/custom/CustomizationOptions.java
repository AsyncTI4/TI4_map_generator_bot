package ti4.commands.custom;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.SpinRingsHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class CustomizationOptions extends GameStateSubcommand {

    public CustomizationOptions() {
        super(Constants.CUSTOMIZATION, "Small Customization Options", true, true);
        List<Choice> onOff = CommandHelper.toChoices("ON", "OFF");
        List<Choice> verbChoices = CommandHelper.toChoices(Constants.VERBOSITY_OPTIONS);
        List<Choice> hexBorderChoices = CommandHelper.toChoices("off", "dash", "solid");

        addOptions(new OptionData(OptionType.STRING, Constants.TEXT_SIZE, "tint/small/medium/large (default = medium)")
                .setAutoComplete(true));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.STRAT_PINGS,
                        "Turn ON or OFF strategy card follow reminders at the start of turn")
                .addChoices(onOff));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.SHOW_FULL_COMPONENT_TEXT,
                "Show full text of components when using/exhausting"));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.VERBOSITY,
                        "Verbosity of bot output. Verbose/Average/Minimal  (Default = Verbose)")
                .addChoices(verbChoices));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.CC_N_PLASTIC_LIMIT,
                        "Turn ON or OFF pings for exceeding component limits")
                .addChoices(onOff));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.BOT_FACTION_REACTS,
                        "Turn ON or OFF the bot leaving your faction react on msgs")
                .addChoices(onOff));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.BOT_COLOR_REACTS,
                        "Turn ON or OFF the bot leaving your color react on msgs")
                .addChoices(onOff));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.BOT_STRAT_REACTS,
                        "Turn ON or OFF the bot leaving your strategy card react on msgs")
                .addChoices(onOff));
        addOptions(new OptionData(
                OptionType.STRING,
                Constants.SPIN_MODE,
                "Automatically spin rings at status cleanup. ON for Fin logic, insert custom logic, OFF to turn off"));
        addOptions(
                new OptionData(OptionType.BOOLEAN, Constants.SHOW_UNIT_TAGS, "Show faction unit tags on map images"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.LIGHT_FOG_MODE, "Retain sight on formerly seen tiles"));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.RED_TAPE_MODE,
                "Reveal all objectives and diplo gets the power to pre-reveal"));
        // addOptions(new OptionData(OptionType.BOOLEAN, Constants.NOMAD_COIN, "Replace TG emojis with nomad coin
        // emojis"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.QUEUE_SO, "Queue secret objective discards"));
        // addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_BUBBLES, "Show the bubbles around
        // anti-bombardment planets"));
        addOptions(
                new OptionData(OptionType.BOOLEAN, Constants.SHOW_GEARS, "Show the production capacity in a system"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.TRANSACTION_METHOD, "Use the new transaction method"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_BANNERS, "Show faction banner at start of turn"));
        addOptions(new OptionData(
                        OptionType.STRING, Constants.SHOW_HEX_BORDERS, "Show borders around systems with player ships")
                .addChoices(hexBorderChoices));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.HOMEBREW_MODE, "Mark the game as homebrew"));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.INJECT_RULES_LINKS,
                "Have the bot inject helpful links to rules within it's output"));
        addOptions(new OptionData(
                OptionType.INTEGER,
                Constants.FAST_SC_FOLLOW,
                "Consider People To Pass on SCs if they don't respond with X hours. Set X to 0 to turn off"));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.UNIT_SOURCE,
                        "Swap player's owned units to units from another source")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        OptionMapping stratPings = event.getOption(Constants.STRAT_PINGS);
        if (stratPings != null) {
            String stratP = stratPings.getAsString();
            if ("ON".equalsIgnoreCase(stratP)) {
                game.setStratPings(true);
            } else if ("OFF".equalsIgnoreCase(stratP)) {
                game.setStratPings(false);
            }
        }

        OptionMapping ccNPlastic = event.getOption(Constants.CC_N_PLASTIC_LIMIT);
        if (ccNPlastic != null) {
            String ccNP = ccNPlastic.getAsString();
            if ("ON".equalsIgnoreCase(ccNP)) {
                game.setCcNPlasticLimit(true);
            } else if ("OFF".equalsIgnoreCase(ccNP)) {
                game.setCcNPlasticLimit(false);
            }
        }
        OptionMapping factReacts = event.getOption(Constants.BOT_FACTION_REACTS);
        if (factReacts != null) {
            String ccNP = factReacts.getAsString();
            if ("ON".equalsIgnoreCase(ccNP)) {
                game.setBotFactionReacts(true);
            } else if ("OFF".equalsIgnoreCase(ccNP)) {
                game.setBotFactionReacts(false);
            }
        }
        OptionMapping colorReacts = event.getOption(Constants.BOT_COLOR_REACTS);
        if (colorReacts != null) {
            String ccNP = colorReacts.getAsString();
            if ("ON".equalsIgnoreCase(ccNP)) {
                game.setBotColorReacts(true);
            } else if ("OFF".equalsIgnoreCase(ccNP)) {
                game.setBotColorReacts(false);
            }
        }

        OptionMapping stratReacts = event.getOption(Constants.BOT_STRAT_REACTS);
        if (stratReacts != null) {
            String ccNP = stratReacts.getAsString();
            if ("ON".equalsIgnoreCase(ccNP)) {
                game.setBotStratReacts(true);
            } else if ("OFF".equalsIgnoreCase(ccNP)) {
                game.setBotStratReacts(false);
            }
        }
        OptionMapping shushing = event.getOption(Constants.SPIN_MODE);
        if (shushing != null) {
            String ccNP = shushing.getAsString();
            if ("ON".equalsIgnoreCase(ccNP)
                    || "OFF".equalsIgnoreCase(ccNP)
                    || SpinRingsHelper.validateSpinSettings(ccNP)) {
                game.setSpinMode(ccNP.toUpperCase());
                MessageHelper.replyToMessage(event, "Spin mode set to `" + ccNP + "`");
            } else {
                MessageHelper.replyToMessage(event, "Invalid spin settings: " + ccNP);
            }
        }

        String textSize = event.getOption(Constants.TEXT_SIZE, null, OptionMapping::getAsString);
        if (textSize != null) game.setTextSize(textSize);

        Boolean showFullTextComponents =
                event.getOption(Constants.SHOW_FULL_COMPONENT_TEXT, null, OptionMapping::getAsBoolean);
        if (showFullTextComponents != null) game.setShowFullComponentTextEmbeds(showFullTextComponents);

        Boolean showUnitTags = event.getOption(Constants.SHOW_UNIT_TAGS, null, OptionMapping::getAsBoolean);
        if (showUnitTags != null) game.setShowUnitTags(showUnitTags);

        Boolean nomad = event.getOption(Constants.NOMAD_COIN, null, OptionMapping::getAsBoolean);
        if (nomad != null) game.setNomadCoin(nomad);

        Boolean light = event.getOption(Constants.LIGHT_FOG_MODE, null, OptionMapping::getAsBoolean);
        if (light != null) game.setLightFogMode(light);
        Boolean red = event.getOption(Constants.RED_TAPE_MODE, null, OptionMapping::getAsBoolean);
        if (red != null) game.setRedTapeMode(red);

        Boolean showB = event.getOption(Constants.SHOW_BUBBLES, null, OptionMapping::getAsBoolean);
        if (showB != null) game.setShowBubbles(showB);

        Boolean transMethod = event.getOption(Constants.TRANSACTION_METHOD, null, OptionMapping::getAsBoolean);
        if (transMethod != null) game.setNewTransactionMethod(transMethod);

        Boolean showG = event.getOption(Constants.SHOW_GEARS, null, OptionMapping::getAsBoolean);
        if (showG != null) game.setShowGears(showG);

        Boolean showBa = event.getOption(Constants.SHOW_BANNERS, null, OptionMapping::getAsBoolean);
        if (showBa != null) game.setShowBanners(showBa);

        String hexStyle = event.getOption(Constants.SHOW_HEX_BORDERS, null, OptionMapping::getAsString);
        if (hexStyle != null) {
            hexStyle = hexStyle.toLowerCase();
            if (hexStyle.equals("dash") || hexStyle.equals("off") || hexStyle.equals("solid")) {
                game.setHexBorderStyle(hexStyle);
            }
        }

        Boolean homebrew = event.getOption(Constants.HOMEBREW_MODE, null, OptionMapping::getAsBoolean);
        if (homebrew != null) game.setHomebrew(homebrew);

        Boolean injectRules = event.getOption(Constants.INJECT_RULES_LINKS, null, OptionMapping::getAsBoolean);
        if (injectRules != null) game.setInjectRulesLinks(injectRules);

        Boolean queueSO = event.getOption(Constants.QUEUE_SO, null, OptionMapping::getAsBoolean);
        if (queueSO != null) {
            game.setQueueSO(queueSO);
            if (!queueSO) {
                String key = "factionsThatAreNotDiscardingSOs";
                String key2 = "queueToDrawSOs";
                String key3 = "potentialBlockers";
                game.setStoredValue(key, "");
                game.setStoredValue(key2, "");
                game.setStoredValue(key3, "");
            }
        }

        Integer fast = event.getOption(Constants.FAST_SC_FOLLOW, null, OptionMapping::getAsInt);
        if (fast != null) {
            game.setStoredValue("fastSCFollow", "" + fast);
            game.setFastSCFollowMode(fast != 0);
        }

        String verbosity = event.getOption(Constants.VERBOSITY, null, OptionMapping::getAsString);
        if (verbosity != null && Constants.VERBOSITY_OPTIONS.contains(verbosity)) game.setOutputVerbosity(verbosity);

        String unitSource = event.getOption(Constants.UNIT_SOURCE, null, OptionMapping::getAsString);
        if (unitSource != null && Mapper.getUnitSourcesDistinct().contains(unitSource))
            game.swapInVariantUnits(unitSource);
    }
}
