package com.acme.salary.employee;

import com.acme.salary.domain.SalaryBand;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SalaryBandRepository extends JpaRepository<SalaryBand, Long> {

  @Query(
      """
      SELECT b FROM SalaryBand b
      JOIN FETCH b.currency
      WHERE b.jobLevel.id = :jobLevelId AND b.country.code = :countryCode
      """)
  Optional<SalaryBand> findFor(Long jobLevelId, String countryCode);
}
