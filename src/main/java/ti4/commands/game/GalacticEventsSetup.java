package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;

class GalacticEventsSetup extends GameStateSubcommand {

    public GalacticEventsSetup() {
        super(Constants.GALACTIC_EVENTS_SETUP, "Game Setup for Galactic Events", true, false);
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.AGE_OF_EXPLORATION_MODE, "True to enable the Age of Exploration, per Codex 4."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.MINOR_FACTIONS_MODE, "True to enable the Minor Factions, per Codex 4.."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.HIDDEN_AGENDA_MODE, "True to enable Hidden Agenda, per Dane Leek."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.AGE_OF_COMMERCE_MODE, "True to enable the Age of Commerce, per Codex 4."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.TOTAL_WAR_MODE, "True to enable Total War, per Codex 4."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.AGE_OF_FIGHTERS_MODE, "True to enable the Age of Fighters, per Thunders Edge."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.CIVILIZED_SOCIETY_MODE, "True to enable Civilized Society, per Thunders Edge."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.STELLAR_ATOMICS_MODE, "True to enable the Stellar Atomics, per Thunders Edge."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DANGEROUS_WILDS_MODE, "True to enable Dangerous Wilds, per Thunders Edge."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        // Boolean betaTestMode = event.getOption(Constants.BETA_TEST_MODE, null, OptionMapping::getAsBoolean);
        // if (betaTestMode != null) game.setTestBetaFeaturesMode(betaTestMode);

        Boolean explorationMode = event.getOption(Constants.AGE_OF_EXPLORATION_MODE, null, OptionMapping::getAsBoolean);
        if (explorationMode != null) game.setAgeOfExplorationMode(explorationMode);

        Boolean minorMode = event.getOption(Constants.MINOR_FACTIONS_MODE, null, OptionMapping::getAsBoolean);
        if (minorMode != null) game.setMinorFactionsMode(minorMode);

        Boolean agendaMode = event.getOption(Constants.HIDDEN_AGENDA_MODE, null, OptionMapping::getAsBoolean);
        if (agendaMode != null) game.setHiddenAgendaMode(agendaMode);

        Boolean totalMode = event.getOption(Constants.TOTAL_WAR_MODE, null, OptionMapping::getAsBoolean);
        if (totalMode != null) game.setTotalWarMode(totalMode);

        Boolean commcerceMode = event.getOption(Constants.AGE_OF_COMMERCE_MODE, null, OptionMapping::getAsBoolean);
        if (commcerceMode != null) game.setAgeOfCommerceMode(commcerceMode);

        Boolean fighterMode = event.getOption(Constants.AGE_OF_FIGHTERS_MODE, null, OptionMapping::getAsBoolean);
        if (fighterMode != null) game.setAgeOfFightersMode(fighterMode);

        Boolean dangerousWildsMode = event.getOption(Constants.DANGEROUS_WILDS_MODE, null, OptionMapping::getAsBoolean);
        if (dangerousWildsMode != null) game.setDangerousWildsMode(dangerousWildsMode);

        Boolean civilizedSocietyMode = event.getOption(Constants.CIVILIZED_SOCIETY_MODE, null, OptionMapping::getAsBoolean);
        if (civilizedSocietyMode != null) game.setCivilizedSocietyMode(civilizedSocietyMode);

        Boolean stellarMode = event.getOption(Constants.STELLAR_ATOMICS_MODE, null, OptionMapping::getAsBoolean);
        if (stellarMode != null) game.setStellarAtomicsMode(stellarMode);
    }

}
