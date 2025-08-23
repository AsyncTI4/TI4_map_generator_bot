package ti4.spring.api.hand;

import java.util.Set;

public record GetHandResponse(Set<String> actionCards, Set<String> secretObjectives, Set<String> promissoryNotes) {}
