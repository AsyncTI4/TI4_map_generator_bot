package ti4.commands.omega_phase;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.DeckModel;

class ResetObjectives extends GameStateSubcommand {
    public ResetObjectives() {
        super(
                Constants.RESET_OMEGA_PHASE_OBJECTIVES,
                "Setup 10 omega phase objectives, with Imperium Rex. Only affects unrevealed/unpeeked objectives.",
                true,
                false);
        addOption(
                OptionType.BOOLEAN,
                Constants.FORCE,
                "Change the objectives even if they're partially revealed, even if the game mode is wrong, etc.",
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        boolean force = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        if (!game.isOmegaPhaseMode() && !force) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "This command only works in Omega Phase mode...unless you use 'force'.");
            return;
        }
        int revealedObjectiveCount = game.getRevealedPublicObjectives().size();
        if (revealedObjectiveCount > 1 && !force) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "There are already revealed objectives. Use 'force' to reset anyway.");
            return;
        }

        setPublicObjectivesStage1Deck(game);
        setPublicObjectivesStage2Deck(game);
    }

    private void setPublicObjectivesStage1Deck(Game game) {
        DeckModel deck = Mapper.getDeck("public_stage_1_objectives_omegaphase");
        if (!game.getStage1PublicDeckID().equals(deck.getAlias())) {
            game.setStage1PublicDeckID(deck.getAlias());
        }

        // Ensure deck is correct
        List<String> allOmegaPhaseObjs = deck.getNewShuffledDeck();
        allOmegaPhaseObjs.removeIf(game.getRevealedPublicObjectives().keySet()::contains);
        allOmegaPhaseObjs.remove(Constants.IMPERIUM_REX_ID);
        allOmegaPhaseObjs.removeIf(game.getPublicObjectives1Peakable()::contains);
        game.setPublicObjectives1(allOmegaPhaseObjs);

        // Remove Imperium Rex if it's already staged
        game.getPublicObjectives1Peakable().removeIf(s -> s.equals(Constants.IMPERIUM_REX_ID));

        // Set up the right number of peakable objectives
        int revealedObjectiveCount = game.getRevealedPublicObjectives().size();
        int customObjectiveCount = game.getCustomPublicVP().size();
        int revealedStage1Count = revealedObjectiveCount - customObjectiveCount;
        int desiredPeakableObjectives = Math.max(0, 9 - revealedStage1Count);
        game.setUpPeakableObjectives(desiredPeakableObjectives, 1);
        int bottomSize = Math.min(5, desiredPeakableObjectives + 1);
        if (bottomSize > 0) {
            game.shuffleInBottomObjective(Constants.IMPERIUM_REX_ID, bottomSize, 1);
        }
    }

    private void setPublicObjectivesStage2Deck(Game game) {
        // No stage 2s in this mode
        game.setUpPeakableObjectives(0, 2);

        DeckModel deck = Mapper.getDeck("public_stage_2_objectives_omegaphase");
        if (!game.getStage2PublicDeckID().equals(deck.getAlias())) {
            game.setStage2PublicDeckID(deck.getAlias());
        }
        game.setPublicObjectives2(new ArrayList<>());
    }
}
