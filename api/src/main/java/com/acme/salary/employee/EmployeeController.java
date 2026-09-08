package com.acme.salary.employee;

import com.acme.salary.common.PageResponse;
import com.acme.salary.employee.dto.CreateEmployeeRequest;
import com.acme.salary.employee.dto.EmployeeDetailDto;
import com.acme.salary.employee.dto.EmployeeSummaryDto;
import com.acme.salary.employee.dto.RecordSalaryChangeRequest;
import com.acme.salary.employee.dto.UpdateEmployeeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Employee directory, detail and pay changes.
 *
 * <p>Thin by design: parameter binding and status codes here, decisions in {@link EmployeeService}.
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

  private final EmployeeService service;

  /**
   * The directory. Always paginated, filters optional.
   *
   * @param sort one of name, salary, hireDate, level, department, country
   */
  @GetMapping
  public PageResponse<EmployeeSummaryDto> search(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Long departmentId,
      @RequestParam(required = false) String countryCode,
      @RequestParam(required = false) Long jobLevelId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "name") String sort,
      @RequestParam(defaultValue = "false") boolean desc,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {

    return service.search(
        new EmployeeSearchCriteria(
            search, departmentId, countryCode, jobLevelId, status, sort, desc, page, size));
  }

  @GetMapping("/{id}")
  public EmployeeDetailDto findOne(@PathVariable long id) {
    return service.findDetail(id);
  }

  @PostMapping
  public ResponseEntity<EmployeeDetailDto> create(
      @Valid @RequestBody CreateEmployeeRequest request, UriComponentsBuilder uri) {

    EmployeeDetailDto created = service.create(request);
    return ResponseEntity.created(uri.path("/api/employees/{id}").build(created.id()))
        .body(created);
  }

  @PutMapping("/{id}")
  public EmployeeDetailDto update(
      @PathVariable long id, @Valid @RequestBody UpdateEmployeeRequest request) {
    return service.update(id, request);
  }

  /**
   * Records a pay change: closes the current record and opens a new one.
   *
   * <p>A POST rather than a PUT on the employee, because this appends to history rather than
   * replacing a value.
   */
  @PostMapping("/{id}/compensation")
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
  public EmployeeDetailDto recordSalaryChange(
      @PathVariable long id, @Valid @RequestBody RecordSalaryChangeRequest request) {
    return service.recordSalaryChange(id, request);
  }
}
