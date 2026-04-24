package ti4.contest.replay.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ti4.contest.replay.core.CombatCandidateStatus;

@Converter(autoApply = true)
public class CombatCandidateStatusConverter implements AttributeConverter<CombatCandidateStatus, String> {

    @Override
    public String convertToDatabaseColumn(CombatCandidateStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public CombatCandidateStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CombatCandidateStatus.valueOf(dbData);
    }
}
