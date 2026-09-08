package com.acme.salary.employee;

import com.acme.salary.common.BusinessRuleException;
import com.acme.salary.common.PageResponse;
import com.acme.salary.common.ResourceNotFoundException;
import com.acme.salary.domain.ChangeReason;
import com.acme.salary.domain.CompensationRecord;
import com.acme.salary.domain.Country;
import com.acme.salary.domain.Department;
import com.acme.salary.domain.Employee;
import com.acme.salary.domain.EmploymentStatus;
import com.acme.salary.domain.Gender;
import com.acme.salary.domain.JobLevel;
import com.acme.salary.domain.SalaryBand;
import com.acme.salary.employee.dto.CompensationRecordDto;
import com.acme.salary.employee.dto.CreateEmployeeRequest;
import com.acme.salary.employee.dto.EmployeeDetailDto;
import com.acme.salary.employee.dto.EmployeeSummaryDto;
import com.acme.salary.employee.dto.MoneyDto;
import com.acme.salary.employee.dto.RecordSalaryChangeRequest;
import com.acme.salary.employee.dto.SalaryBandDto;
import com.acme.salary.employee.dto.UpdateEmployeeRequest;
import com.acme.salary.money.FxRateService;
import com.acme.salary.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeService {

  private final EmployeeRepository employees;
  private final CompensationRecordRepository compensation;
  private final CountryRepository countries;
  private final DepartmentRepository departments;
  private final JobLevelRepository jobLevels;
  private final SalaryBandRepository bands;
  private final EmployeeDirectoryQuery directory;
  private final FxRateService fxRates;

  // ------------------------------------------------------------------------ read

  @Transactional(readOnly = true)
  public PageResponse<EmployeeSummaryDto> search(EmployeeSearchCriteria criteria) {
    return directory.search(criteria);
  }

  @Transactional(readOnly = true)
  public EmployeeDetailDto findDetail(long id) {
    Employee employee =
        employees
            .findDetailById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", id));

    List<CompensationRecord> history = compensation.findHistory(id);
    FxRateService.Snapshot snapshot = fxRates.latestSnapshot();

    Optional<CompensationRecord> current =
        history.stream().filter(CompensationRecord::isCurrent).findFirst();

    MoneyDto currentSalary = current.map(r -> toMoneyDto(r, snapshot)).orElse(null);
    SalaryBandDto band =
        bands
            .findFor(employee.getJobLevel().getId(), employee.getCountry().getCode())
            .map(b -> toBandDto(b, current.map(CompensationRecord::getAmountMinor).orElse(null)))
            .orElse(null);

    return new EmployeeDetailDto(
        employee.getId(),
        employee.getEmployeeCode(),
        employee.getFirstName(),
        employee.getLastName(),
        employee.getFullName(),
        employee.getEmail(),
        employee.getJobTitle(),
        employee.getDepartment().getName(),
        employee.getCountry().getName(),
        employee.getJobLevel().getCode(),
        employee.getHireDate(),
        employee.getEmploymentStatus().name(),
        currentSalary,
        band,
        toHistoryDtos(history, snapshot));
  }

  // ----------------------------------------------------------------------- write

  @Transactional
  public EmployeeDetailDto create(CreateEmployeeRequest request) {
    if (employees.existsByEmail(request.email())) {
      throw new BusinessRuleException(
          "An employee with email %s already exists".formatted(request.email()));
    }

    Country country =
        countries
            .findWithCurrency(request.countryCode())
            .orElseThrow(() -> new ResourceNotFoundException("Country", request.countryCode()));
    Department department =
        departments
            .findById(request.departmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Department", request.departmentId()));
    JobLevel level =
        jobLevels
            .findById(request.jobLevelId())
            .orElseThrow(() -> new ResourceNotFoundException("Job level", request.jobLevelId()));

    Employee employee = new Employee();
    employee.setFirstName(request.firstName().trim());
    employee.setLastName(request.lastName().trim());
    employee.setEmail(request.email().trim().toLowerCase());
    employee.setCountry(country);
    employee.setDepartment(department);
    employee.setJobLevel(level);
    employee.setJobTitle(request.jobTitle().trim());
    employee.setHireDate(request.hireDate());
    employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
    employee.setGender(parseGender(request.gender()));
    // employee_code is derived rather than supplied: it is an internal identifier, and
    // letting callers choose it invites collisions the database would then reject.
    employee.setEmployeeCode(nextEmployeeCode());
    employees.saveAndFlush(employee);

    // Starting pay is effective from the hire date -- an employee is never unpaid.
    Money amount =
        Money.ofMajor(
            request.startingSalary(),
            country.getCurrency().getCode(),
            country.getCurrency().getExponent());

    CompensationRecord initial = new CompensationRecord();
    initial.setEmployee(employee);
    initial.setAmountMinor(amount.amountMinor());
    initial.setCurrency(country.getCurrency());
    initial.setEffectiveFrom(request.hireDate());
    initial.setChangeReason(ChangeReason.INITIAL);
    compensation.saveAndFlush(initial);

    return findDetail(employee.getId());
  }

  @Transactional
  public EmployeeDetailDto update(long id, UpdateEmployeeRequest request) {
    Employee employee =
        employees
            .findDetailById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", id));

    Department department =
        departments
            .findById(request.departmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Department", request.departmentId()));
    JobLevel level =
        jobLevels
            .findById(request.jobLevelId())
            .orElseThrow(() -> new ResourceNotFoundException("Job level", request.jobLevelId()));

    employee.setFirstName(request.firstName().trim());
    employee.setLastName(request.lastName().trim());
    employee.setEmail(request.email().trim().toLowerCase());
    employee.setDepartment(department);
    employee.setJobLevel(level);
    employee.setJobTitle(request.jobTitle().trim());
    employee.setEmploymentStatus(parseStatus(request.employmentStatus()));
    employees.saveAndFlush(employee);

    return findDetail(id);
  }

  /**
   * Records a pay change.
   *
   * <p>The current record is closed at the new effective date and a new one opened -- nothing is
   * overwritten, so the history stays complete. The database independently enforces non-overlap and
   * one-current-record, so a concurrent duplicate surfaces as a conflict rather than corrupting the
   * chain.
   */
  @Transactional
  public EmployeeDetailDto recordSalaryChange(long id, RecordSalaryChangeRequest request) {
    Employee employee =
        employees
            .findDetailById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", id));

    CompensationRecord current =
        compensation
            .findCurrent(id)
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        "This employee has no current compensation record to supersede"));

    if (!request.effectiveFrom().isAfter(current.getEffectiveFrom())) {
      throw new BusinessRuleException(
          "effectiveFrom (%s) must be after the current record's start date (%s)"
              .formatted(request.effectiveFrom(), current.getEffectiveFrom()));
    }

    Money amount =
        Money.ofMajor(
            request.amount(),
            employee.getCountry().getCurrency().getCode(),
            employee.getCountry().getCurrency().getExponent());

    if (amount.amountMinor() == current.getAmountMinor()) {
      throw new BusinessRuleException("The new amount is the same as the current one");
    }

    current.setEffectiveTo(request.effectiveFrom());
    compensation.saveAndFlush(current);

    CompensationRecord next = new CompensationRecord();
    next.setEmployee(employee);
    next.setAmountMinor(amount.amountMinor());
    next.setCurrency(employee.getCountry().getCurrency());
    next.setEffectiveFrom(request.effectiveFrom());
    next.setChangeReason(parseReason(request.changeReason()));
    next.setNote(request.note());
    compensation.saveAndFlush(next);

    return findDetail(id);
  }

  // --------------------------------------------------------------------- mapping

  private List<CompensationRecordDto> toHistoryDtos(
      List<CompensationRecord> history, FxRateService.Snapshot snapshot) {
    List<CompensationRecordDto> dtos = new ArrayList<>(history.size());
    for (int i = 0; i < history.size(); i++) {
      CompensationRecord record = history.get(i);
      BigDecimal changePercent = null;
      if (i > 0) {
        long previous = history.get(i - 1).getAmountMinor();
        if (previous > 0) {
          changePercent =
              BigDecimal.valueOf(record.getAmountMinor() - previous)
                  .multiply(BigDecimal.valueOf(100))
                  .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
        }
      }
      dtos.add(
          new CompensationRecordDto(
              record.getId(),
              toMoneyDto(record, snapshot),
              record.getEffectiveFrom(),
              record.getEffectiveTo(),
              record.getChangeReason().name(),
              record.getNote(),
              changePercent));
    }
    return dtos;
  }

  private MoneyDto toMoneyDto(CompensationRecord record, FxRateService.Snapshot snapshot) {
    String currency = record.getCurrency().getCode();
    Money money = new Money(record.getAmountMinor(), currency, record.getCurrency().getExponent());
    return new MoneyDto(
        money.amountMinor(), currency, money.toMajor(), money.toBase(snapshot.rateFor(currency)));
  }

  private SalaryBandDto toBandDto(SalaryBand band, Long currentAmountMinor) {
    BigDecimal compaRatio = null;
    String position = "UNKNOWN";
    if (currentAmountMinor != null) {
      compaRatio = Money.compaRatio(currentAmountMinor, band.getMidMinor());
      if (currentAmountMinor < band.getMinMinor()) {
        position = "BELOW";
      } else if (currentAmountMinor > band.getMaxMinor()) {
        position = "ABOVE";
      } else {
        position = "WITHIN";
      }
    }
    return new SalaryBandDto(
        band.getMinMinor(),
        band.getMidMinor(),
        band.getMaxMinor(),
        band.getCurrency().getCode(),
        compaRatio,
        position);
  }

  // --------------------------------------------------------------------- parsing

  private String nextEmployeeCode() {
    return "ACME%05d".formatted(employees.count() + 1);
  }

  private Gender parseGender(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return parseEnum(Gender.class, value, "gender");
  }

  private EmploymentStatus parseStatus(String value) {
    return parseEnum(EmploymentStatus.class, value, "employmentStatus");
  }

  private ChangeReason parseReason(String value) {
    return parseEnum(ChangeReason.class, value, "changeReason");
  }

  private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
    try {
      return Enum.valueOf(type, value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown %s '%s'. Supported: %s"
              .formatted(field, value, List.of(type.getEnumConstants())));
    }
  }
}
