package ti4.service.strategycard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.model.StrategyCardModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;

@UtilityClass
public class StrategyCardSecondaryButtonService {

    public static List<Button> getSecondaryAbilityButtons(Game game, Collection<Integer> scs) {
        Map<String, Button> buttons = new LinkedHashMap<>();
        for (int sc : scs) {
            if (sc <= 0) continue;
            for (Button button : getSecondaryAbilityButtons(game, sc)) {
                buttons.putIfAbsent(button.getCustomId(), button);
            }
        }
        return new ArrayList<>(buttons.values());
    }

    public static List<Button> getSecondaryAbilityButtons(Game game, int sc) {
        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
        if (scModel == null) {
            return List.of();
        }
        return switch (scModel.getBotSCAutomationID()) {
            case "pok1leadership", "anarchy1" -> List.of(Buttons.SPEND_AND_GAIN_COMMAND_TOKENS);
            case "pok2diplomacy", "base2", "luminous1" -> List.of(Buttons.DIPLO_READY_2_PLANETS);
            case "anarchy2" -> List.of(Buttons.READY_A_COMPONENT, Buttons.DIPLO_READY_PLANETS);
            case "anarchy3", "luminous9" ->
                List.of(Buttons.gray("anarchy3secondary", "Perform Unchosen Or Exhausted Secondary"));
            case "luminous2" ->
                List.of(
                        Buttons.gray("lumiACdraw", "Draw 1 Action Card", CardEmojis.getACEmoji(game)),
                        Buttons.gray("exploreAPlanet", "Explore a planet"));
            case "pok3politics", "evenfall3", "ignisaurora3" ->
                List.of(Buttons.gray("draw2 AC", "Draw 2 Action Cards", CardEmojis.getACEmoji(game)));
            case "cryypter_3" ->
                List.of(Buttons.gray("cryypterSC3Draw", "Draw Action Cards", CardEmojis.getACEmoji(game)));
            case "pok4construction", "te4construction", "monuments4construction", "monumentstf4" ->
                getConstructionButtons(game, scModel);
            case "pok5trade" -> List.of(Buttons.REPLENISH_COMMODITIES);
            case "pok6warfare", "anarchy7", "luminous7" -> List.of(Buttons.WARFARE_BUILD_AT_HOME);
            case "te6warfare" -> List.of(Buttons.WARFARE_TE_BUILD_AT_HOME);
            case "anarchy8" -> List.of(Buttons.green("resolveAnarchy8Secondary", "Lift Command Token"));
            case "anarchy10" -> List.of(Buttons.gray("anarchy10PeekStart", "Peek at Public", CardEmojis.Public1));
            case "tf2" -> List.of(Buttons.blue("participateInSplice_2", "Participate In Splice"));
            case "tf6" ->
                List.of(
                        Buttons.WARFARE_TE_BUILD_AT_HOME,
                        Buttons.blue("participateInSplice_6", "Participate In Splice"));
            case "tf7" -> List.of(Buttons.blue("participateInSplice_7", "Participate In Splice"));
            case "tf8" -> List.of(Buttons.NON_SC_DRAW_SECRET_OBJECTIVE, Buttons.gray("drawParadigm", "Draw Paradigm"));
            case "ignisaurora2" ->
                List.of(Buttons.green("ignisAuroraSC8Secondary", "Draw Unknown Relic Fragment", ExploreEmojis.UFrag));
            case "pok7technology", "manytech8", "manytech9", "manytech10", "manytech11", "manytech12" ->
                List.of(Buttons.GET_A_TECH);
            case "pok8imperial", "anarchy11", "manytech13" -> List.of(Buttons.NON_SC_DRAW_SECRET_OBJECTIVE);
            default -> List.of();
        };
    }

    private static List<Button> getConstructionButtons(Game game, StrategyCardModel scModel) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.CONSTRUCTION_PLACE_1_SPACE_DOCK);
        buttons.add(Buttons.CONSTRUCTION_PLACE_1_PDS);
        if (game.isFacilitiesMode()) {
            buttons.add(Buttons.green("construction_facility", "Place A Facility"));
        }
        if (game.isMonumentToTheAgesMode()) {
            buttons.add(Buttons.CONSTRUCTION_PLACE_A_MONUMENT);
        }
        return buttons;
    }
}
