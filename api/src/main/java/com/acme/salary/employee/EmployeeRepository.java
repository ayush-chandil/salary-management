package com.acme.salary.employee;

import com.acme.salary.domain.Employee;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

  /**
   * Loads an employee with its reference associations already fetched.
   *
   * <p>The associations are LAZY and {@code open-in-view} is off, so building the detail response
   * without this would either fail outside the transaction or fire four extra queries.
   */
  @Query(
      """
      SELECT e FROM Employee e
      JOIN FETCH e.country c
      JOIN FETCH c.currency
      JOIN FETCH e.department
      JOIN FETCH e.jobLevel
      WHERE e.id = :id
      """)
  Optional<Employee> findDetailById(Long id);

  boolean existsByEmail(String email);
}
