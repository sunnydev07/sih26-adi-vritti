package in.adivritti.core.admin.repository;

import in.adivritti.core.admin.entity.CoverageCandidate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoverageCandidateRepository extends JpaRepository<CoverageCandidate, UUID> {

    /**
     * School-level coverage gap aggregation. Counts are computed in SQL rather than by
     * loading candidates into memory, so the endpoint stays flat as the table grows.
     * Null filter arguments match "any value" and are passed through directly.
     */
    @Query("""
        select c.school, c.block, c.district, c.state,
               count(c),
               sum(case when c.pvtgStatus then 1 else 0 end)
        from CoverageCandidate c
        where (:state is null or c.state = :state)
          and (:district is null or c.district = :district)
          and (:block is null or c.block = :block)
          and (:school is null or c.school = :school)
          and (:pvtgOnly = false or c.pvtgStatus = true)
          and c.outreachStatus = 'unreached'
        group by c.school, c.block, c.district, c.state
        order by count(c) desc
        """)
    List<Object[]> aggregateGapsBySchool(@Param("state") String state,
        @Param("district") String district,
        @Param("block") String block,
        @Param("school") String school,
        @Param("pvtgOnly") boolean pvtgOnly);
}
