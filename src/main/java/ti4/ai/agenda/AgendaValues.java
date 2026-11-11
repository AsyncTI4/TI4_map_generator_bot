package ti4.ai.agenda;

import java.util.OptionalDouble;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class AgendaValues {

    private static final Set<String> DEPENDS_ON_OWN_VOTE = Set.of(
            "conventions",
            "mutiny",
            "unconventional",
            "wormhole_research",
            "representative_government",
            "articles_war",
            "rep_govt");

    public static boolean dependsOnOwnVote(String agenda) {
        return DEPENDS_ON_OWN_VOTE.contains(agenda);
    }

    static OptionalDouble of(Game game, Player voter, String agenda, String outcome, String ownVote) {
        return of(game, voter, false, agenda, outcome, ownVote);
    }

    static OptionalDouble of(Game game, Player voter, boolean ownView, String agenda, String outcome, String ownVote) {
        if (agenda == null) return OptionalDouble.empty();
        Double value = expertValue(game, voter, ownView, agenda, outcome, ownVote);
        if (value == null) value = valueOf(game, voter, agenda, outcome, ownVote);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    @Nullable
    private static Double expertValue(
            Game game, Player voter, boolean ownView, String agenda, String outcome, String ownVote) {
        if (!AiExperiments.enabled(voter, Experiment.AGENDA_VALUE_TABLE)) return null;
        return ExpertAgendaValues.valueOf(new AgendaView(game, voter, ownView), agenda, outcome, ownVote);
    }

    static double ownVoteAdjustment(Game game, Player voter, String agenda, String winner, String ownVote) {
        if (!dependsOnOwnVote(agenda)) return 0;
        OptionalDouble actual = of(game, voter, true, agenda, winner, ownVote);
        OptionalDouble assumed = of(game, voter, true, agenda, winner, winner);
        if (actual.isEmpty() || assumed.isEmpty()) return 0;
        return actual.getAsDouble() - assumed.getAsDouble();
    }

    @Nullable
    private static Double valueOf(Game game, Player voter, String agenda, String outcome, String ownVote) {
        return switch (agenda) {
            case "secret" -> ElectionValues.archivedSecret(game, voter, outcome);
            case "committee" -> ElectionValues.committeeFormation(game, voter, outcome);
            case "arbiter" -> ElectionValues.imperialArbiter(game, voter, outcome);
            case "minister_commerce" -> ElectionValues.ministerOfCommerce(game, voter, outcome);
            case "minister_exploration" -> ElectionValues.ministerOfExploration(game, voter, outcome);
            case "minister_industry" -> ElectionValues.ministerOfIndustry(game, voter, outcome);
            case "minister_peace" -> ElectionValues.ministerOfPeace(game, voter, outcome);
            case "minister_policy" -> ElectionValues.ministerOfPolicy(game, voter, outcome);
            case "minister_sciences" -> ElectionValues.ministerOfSciences(game, voter, outcome);
            case "minister_war" -> ElectionValues.ministerOfWar(game, voter, outcome);
            case "minister_antiquities" -> ElectionValues.ministerOfAntiquities(game, voter, outcome);
            case "prophecy" -> ElectionValues.prophecyOfIxth(game, voter, outcome);
            case "execution" -> ElectionValues.publicExecution(game, voter, outcome);
            case "shard_of_the_throne" -> ElectionValues.shardOfTheThrone(game, voter, outcome);
            case "crown_of_emphidia" -> ElectionValues.crownOfEmphidia(game, voter, outcome);
            case "crown_of_thalnos" -> ElectionValues.crownOfThalnos(game, voter, outcome);
            case "standardization" -> ElectionValues.armedForcesStandardization(game, voter, outcome);
            case "covert" -> ElectionValues.covertLegislation(game, voter, outcome);
            case "censure" -> ElectionValues.politicalCensure(game, voter, outcome);
            case "grant_reallocation" -> ElectionValues.researchGrantReallocation(game, voter, outcome);
            case "warrant" -> ElectionValues.searchWarrant(game, voter, outcome);
            case "redistribution" -> PlanetElectionValues.colonialRedistribution(game, voter, outcome);
            case "disarmament" -> PlanetElectionValues.compensatedDisarmament(game, voter, outcome);
            case "core_mining" -> PlanetElectionValues.coreMining(game, voter, outcome);
            case "demilitarized_zone" -> PlanetElectionValues.demilitarizedZone(game, voter, outcome);
            case "holy_planet_of_ixth" -> PlanetElectionValues.holyPlanetOfIxth(game, voter, outcome);
            case "rt_biotic" -> PlanetElectionValues.researchTeamBiotic(game, voter, outcome);
            case "rt_cybernetic" -> PlanetElectionValues.researchTeamCybernetic(game, voter, outcome);
            case "rt_propulsion" -> PlanetElectionValues.researchTeamPropulsion(game, voter, outcome);
            case "rt_warfare" -> PlanetElectionValues.researchTeamWarfare(game, voter, outcome);
            case "senate_sanctuary" -> PlanetElectionValues.senateSanctuary(game, voter, outcome);
            case "terraforming_initiative" -> PlanetElectionValues.terraformingInitiative(game, voter, outcome);
            case "revolution" -> ForAgainstLawValues.antiIntellectualRevolution(game, voter, outcome);
            case "conventions" -> ForAgainstLawValues.conventionsOfWar(game, voter, outcome, ownVote);
            case "travel_ban" -> ForAgainstLawValues.enforcedTravelBan(game, voter, outcome);
            case "sanctions" -> ForAgainstLawValues.executiveSanctions(game, voter, outcome);
            case "regulations" -> ForAgainstLawValues.fleetRegulations(game, voter, outcome);
            case "defense_act" -> ForAgainstLawValues.homelandDefenseAct(game, voter, outcome);
            case "schematics" -> ForAgainstLawValues.publicizeWeaponSchematics(game, voter, outcome);
            case "conscription" -> ForAgainstLawValues.regulatedConscription(game, voter, outcome);
            case "shared_research" -> ForAgainstLawValues.sharedResearch(game, voter, outcome);
            case "wormhole_recon" -> ForAgainstLawValues.wormholeReconstruction(game, voter, outcome);
            case "representative_government", "rep_govt" ->
                ForAgainstLawValues.representativeGovernment(game, voter, outcome, ownVote);
            case "articles_war" -> ForAgainstLawValues.articlesOfWar(game, voter, outcome, ownVote);
            case "checks" -> ForAgainstLawValues.checksAndBalances(game, voter, outcome);
            case "nexus" -> ForAgainstLawValues.nexusSovereignty(game, voter, outcome);
            case "arms_reduction" -> DirectiveValues.armsReduction(game, voter, outcome);
            case "economic_equality" -> DirectiveValues.economicEquality(game, voter, outcome);
            case "incentive" -> DirectiveValues.incentiveProgram(game, voter, outcome);
            case "artifact" -> DirectiveValues.ixthianArtifact(game, voter, outcome);
            case "mutiny" -> DirectiveValues.mutiny(game, voter, outcome, ownVote);
            case "seed_empire" -> DirectiveValues.seedOfAnEmpire(game, voter, outcome);
            case "plowshares" -> DirectiveValues.swordsToPlowshares(game, voter, outcome);
            case "unconventional" -> DirectiveValues.unconventionalMeasures(game, voter, outcome, ownVote);
            case "wormhole_research" -> DirectiveValues.wormholeResearch(game, voter, outcome, ownVote);
            case "cladenstine" -> DirectiveValues.clandestineOperations(game, voter, outcome);
            case "rearmament" -> DirectiveValues.rearmamentAgreement(game, voter, outcome);
            case "abolishment" -> LawChangeValues.judicialAbolishment(game, voter, outcome);
            case "miscount" -> LawChangeValues.miscountDisclosed(game, voter, outcome);
            case "constitution" -> LawChangeValues.newConstitution(game, voter, outcome);
            case "classified" -> CardElectionValues.classifiedDocumentLeaks(game, voter, outcome);
            case "crisis" -> CardElectionValues.galacticCrisisPact(game, voter, outcome);
            default -> null;
        };
    }
}
