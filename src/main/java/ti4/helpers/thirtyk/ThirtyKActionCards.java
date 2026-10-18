package ti4.helpers.thirtyk;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.actioncards.thirtyk.ThirtyKAgendaAcButtonHandler;
import ti4.discord.interactions.buttons.handlers.actioncards.thirtyk.ThirtyKBoardAcButtonHandler;
import ti4.discord.interactions.buttons.handlers.actioncards.thirtyk.ThirtyKEconomyAcButtonHandler;
import ti4.discord.interactions.buttons.handlers.actioncards.thirtyk.ThirtyKPoliticsAcButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.Source.ComponentSource;

@UtilityClass
public class ThirtyKActionCards {

    private static final Set<String> RIDER_CARDS = Set.of("embassyrider30k", "dummyrider30k", "publicoutcry30k");

    public static boolean resolveActionCard(ActionCardModel card, Player player, String introMsg) {
        if (card.getSource() != ComponentSource.thirtyk) return false;
        if (RIDER_CARDS.contains(ThirtyKCards.baseId(card.getAlias()))) {
            ThirtyKAcState.markRiderCard(player.getGame(), player, card.getName());
        }
        List<Button> buttons = resolveButtons(card, player);
        if (buttons == null) return false;
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", _" + card.getName() + "_ has no legal target.");
            return true;
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), introMsg, buttons);
        return true;
    }

    static List<Button> resolveButtons(ActionCardModel card, Player player) {
        Game game = player.getGame();
        String ffcc = player.factionButtonChecker();
        String resolve = "Resolve " + card.getName();
        List<Button> buttons = new ArrayList<>();
        switch (ThirtyKCards.baseId(card.getAlias())) {
            case "compoundinterest30k" -> buttons.addAll(ThirtyKEconomyAcButtonHandler.compoundInterestButtons(player));
            case "conflagration30k" ->
                buttons.add(Buttons.green(ffcc + ThirtyKBoardAcButtonHandler.CONFLAGRATION, resolve));
            case "denounceleadership30k" ->
                buttons.add(Buttons.green(ffcc + ThirtyKPoliticsAcButtonHandler.DENOUNCE, resolve));
            case "extendedcampaigning30k" ->
                buttons.addAll(ThirtyKPoliticsAcButtonHandler.extendedCampaigningButtons(game, player));
            case "tradeships30k" -> buttons.addAll(ThirtyKPoliticsAcButtonHandler.tradeShipsButtons(game, player));
            case "warprofiteering30k" ->
                buttons.addAll(ThirtyKEconomyAcButtonHandler.warProfiteeringButtons(game, player));
            case "publicreprimand30k" -> buttons.addAll(ThirtyKEconomyAcButtonHandler.reprimandButtons(game, player));
            case "devisiveplot30k" -> buttons.addAll(ThirtyKPoliticsAcButtonHandler.devisivePlotButtons(game, player));
            case "finalstand30k" -> buttons.add(Buttons.green(ffcc + ThirtyKBoardAcButtonHandler.FINAL_STAND, resolve));
            case "preservationact30k" ->
                buttons.add(Buttons.green(ffcc + ThirtyKEconomyAcButtonHandler.PRESERVATION, resolve));
            case "hidewithinthefineprint30k" ->
                buttons.add(Buttons.green(
                        ffcc + ThirtyKAgendaAcButtonHandler.RIDER_COPY_LIST + ThirtyKAgendaAcButtonHandler.SAME,
                        resolve));
            case "objectiongambit30k" ->
                buttons.add(Buttons.green(
                        ffcc + ThirtyKAgendaAcButtonHandler.RIDER_COPY_LIST + ThirtyKAgendaAcButtonHandler.OPPOSITE,
                        resolve));
            case "siphonfundsjmsc" ->
                buttons.add(Buttons.green(ffcc + ThirtyKEconomyAcButtonHandler.SIPHON_START, resolve));
            case "priceofinformationjmsc" ->
                buttons.addAll(ThirtyKEconomyAcButtonHandler.priceOfInformationButtons(game, player));
            case "andthentheyblewupjmsc" ->
                buttons.add(Buttons.green(ffcc + ThirtyKBoardAcButtonHandler.BLEW_UP, resolve));
            case "foldingofspacetimejmsc" ->
                buttons.add(Buttons.green(ffcc + ThirtyKBoardAcButtonHandler.FOLDING, resolve));
            case "antidisestablishmentarianismjmsc" ->
                buttons.add(Buttons.green(ffcc + ThirtyKEconomyAcButtonHandler.ANTIDIS, resolve));
            case "distantmapsjmsc" ->
                buttons.add(Buttons.green(ffcc + ThirtyKBoardAcButtonHandler.DISTANT_MAPS, resolve));
            case "unverifiedforgeriesjmsc" ->
                buttons.addAll(ThirtyKEconomyAcButtonHandler.forgeriesButtons(game, player));
            case "subjectivejusticejmsc" ->
                buttons.addAll(ThirtyKAgendaAcButtonHandler.subjectiveJusticeButtons(game, player));
            case "extremerenewaljmsc" -> buttons.addAll(ThirtyKBoardAcButtonHandler.renewalButtons(game, player));
            case "cumulativesupportjmsc" ->
                buttons.add(Buttons.green(ffcc + ThirtyKBoardAcButtonHandler.CUMULATIVE, resolve));
            case "corruptmanifestationjmsc" ->
                buttons.add(Buttons.green(ffcc + ThirtyKBoardAcButtonHandler.CORRUPT, resolve));
            case "creationofgloryjmsc" -> buttons.add(relicReveal(ffcc, ThirtyKPoliticsAcButtonHandler.RESOURCES));
            case "propheticdeclarationjmsc" -> buttons.add(relicReveal(ffcc, ThirtyKPoliticsAcButtonHandler.INFLUENCE));
            case "resourcecondensationjmsc" ->
                buttons.add(relicReveal(ffcc, ThirtyKPoliticsAcButtonHandler.COMMAND_TOKENS));
            default -> {
                return null;
            }
        }
        return buttons;
    }

    private static Button relicReveal(String ffcc, String mode) {
        return Buttons.green(ffcc + ThirtyKPoliticsAcButtonHandler.RELIC_REVEAL + mode, "Reveal The Top Relic");
    }
}
