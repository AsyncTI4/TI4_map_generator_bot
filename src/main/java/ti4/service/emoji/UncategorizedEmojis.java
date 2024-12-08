package ti4.service.emoji;

public enum UncategorizedEmojis implements TI4Emoji {
    // Explores
    HFrag, CFrag, IFrag, UFrag, Relic, Cultural, Industrial, Hazardous, Frontier,

    // Cards
    ActionCard, ActionCardAlt, Agenda, AgendaAlt, PN, RelicCard, CulturalCard, HazardousCard, IndustrialCard, FrontierCard,

    // Objectives
    Custodians, CustodiansVP, SecretObjective, Public1, Public2, Public1alt, Public2alt, SecretObjectiveAlt,

    // Tokens
    tg, NomadCoin, comm, Sleeper, dmz,

    // units
    warsun, spacedock, pds, mech, infantry, flagship, fighter, dreadnought, destroyer, carrier, cruiser, TyrantsLament, PlenaryOrbital, Monument,

    // Res / Inf
    influence, resources, ResInf, //
    Resources_0, Resources_1, Resources_2, Resources_3, Resources_4, //
    Resources_5, Resources_6, Resources_7, Resources_8, Resources_9, //
    Influence_0, Influence_1, Influence_2, Influence_3, Influence_4, //
    Influence_5, Influence_6, Influence_7, Influence_8, Influence_9, //

    // Tiles
    Supernova, Asteroids, GravityRift, Nebula, Anomaly, EmptySystem, Nexus,

    // Tech
    PropulsionTech, PropulsionDisabled, Propulsion2, Propulsion3, //
    BioticTech, BioticDisabled, Biotic2, Biotic3, //
    CyberneticTech, CyberneticDisabled, Cybernetic2, Cybernetic3, //
    WarfareTech, WarfareDisabled, Warfare2, Warfare3, //
    UnitUpgradeTech, UnitTechSkip, NonUnitTechSkip,

    // Doggies
    Winnie, Ozzie, Summer, Charlie, Scout, ScoutSpinner,

    // Franken Emblems
    franken_aurilian_vanguard, franken_aelorian_clans, franken_dakari_hegemony, franken_durethian_shard, franken_elyndor_consortium, //
    franken_fal_kesh_covenant, franken_ghaldir_union, franken_helian_imperium, franken_jhoran_brotherhood, franken_kyrenic_republic, //
    franken_lysarian_order, franken_mydran_assembly, franken_nyridian_coalition, franken_olthax_collective, franken_qalorian_federation, //
    franken_prayers_of_trudval, franken_rak_thul_tribes, franken_sol_tari_dynasty, franken_syrketh_conclave, franken_thalassian_guild, //
    franken_thraxian_imperium, franken_thymarian_league, franken_valxian_pact, franken_var_sul_syndicate, franken_veridian_empire, //
    franken_zel_tharr_dominion, franken_zircon_ascendancy, franken_zor_thul_matriarchate, //

    // Other
    WHalpha, WHbeta, WHgamma, CreussAlpha, CreussBeta, CreussGamma, //
    LegendaryPlanet, SpeakerToken, Sabotage, NoSabo, NoWhens, NoAfters, //
    Wash, Winemaking, BortWindow, SpoonAbides, AsyncTI4Logo, TIGL, RollDice, //
    BLT, Stroter;

    @Override
    public String toString() {
        return emojiString();
    }
}
