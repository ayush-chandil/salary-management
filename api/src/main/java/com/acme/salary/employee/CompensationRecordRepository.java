package com.acme.salary.employee;

import com.acme.salary.domain.CompensationRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompensationRecordRepository extends JpaRepository<CompensationRecord, Long> {

  /** Full pay history, oldest first, with currency fetched for formatting. */
  @Query(
      """
      SELECT r FROM CompensationRecord r
      JOIN FETCH r.currency
      WHERE r.employee.id = :employeeId
      ORDER BY r.effectiveFrom ASC
      """)
  List<CompensationRecord> findHistory(Long employeeId);

  /** The open record, if the employee has one. Terminated employees do not. */
  @Query(
      """
      SELECT r FROM CompensationRecord r
      JOIN FETCH r.currency
      WHERE r.employee.id = :employeeId AND r.effectiveTo IS NULL
      """)
  Optional<CompensationRecord> findCurrent(Long employeeId);
}
