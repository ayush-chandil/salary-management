package com.acme.salary.employee;

import com.acme.salary.domain.Country;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CountryRepository extends JpaRepository<Country, String> {

  /** Currency is needed for every money calculation, so fetch it rather than lazy-load it. */
  @Query("SELECT c FROM Country c JOIN FETCH c.currency WHERE c.code = :code")
  Optional<Country> findWithCurrency(String code);
}
