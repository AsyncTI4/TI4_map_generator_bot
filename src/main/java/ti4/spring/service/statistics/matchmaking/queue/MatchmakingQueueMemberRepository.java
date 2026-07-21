package ti4.spring.service.statistics.matchmaking.queue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MatchmakingQueueMemberRepository extends JpaRepository<MatchmakingQueueMember, Long> {

    Optional<MatchmakingQueueMember> findByUserId(String userId);

    boolean existsByUserId(String userId);

    List<MatchmakingQueueMember> findAllByPartyId(long partyId);

    List<MatchmakingQueueMember> findAllByPartyIdIn(Collection<Long> partyIds);

    @Modifying
    @Query("delete from MatchmakingQueueMember member where member.id = :memberId")
    int deleteMember(@Param("memberId") long memberId);

    @Modifying
    @Query("delete from MatchmakingQueueMember member where member.partyId in :partyIds")
    int deleteAllForParties(@Param("partyIds") Collection<Long> partyIds);

    @Modifying
    @Query("delete from MatchmakingQueueMember")
    int deleteAllMembers();
}
