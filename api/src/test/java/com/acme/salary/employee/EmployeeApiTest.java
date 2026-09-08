package com.acme.salary.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.salary.TestcontainersConfiguration;
import com.acme.salary.seed.DataSeeder;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end tests over the real HTTP layer and a real Postgres.
 *
 * <p>Reseeded before every test. Seeding once for the class was tried first and made the row-count
 * assertions depend on test execution order, because the write tests add employees. A test that
 * passes or fails based on ordering is worse than a slightly slower suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmployeeApiTest {

  /** Small enough to reseed quickly, large enough to span every country and level. */
  private static final int SEEDED = 100;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSeeder seeder;

  @BeforeEach
  void reseed() {
    seeder.seed(SEEDED, 99L, true);
  }

  // ------------------------------------------------------------------- directory

  @Test
  void returnsAPageRatherThanEverything() throws Exception {
    mvc.perform(get("/api/employees").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(10)))
        .andExpect(jsonPath("$.totalElements", is(SEEDED)))
        .andExpect(jsonPath("$.totalPages", is(SEEDED / 10)))
        .andExpect(jsonPath("$.page", is(0)));
  }

  @Test
  void capsPageSizeSoCallersCannotRequestEverything() throws Exception {
    // ?size=100000 would otherwise be an unbounded response with extra steps.
    mvc.perform(get("/api/employees").param("size", "100000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size", is(EmployeeSearchCriteria.MAX_SIZE)));
  }

  @Test
  void negativePageSizeFallsBackToTheDefault() throws Exception {
    mvc.perform(get("/api/employees").param("size", "-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size", is(lessThanOrEqualTo(EmployeeSearchCriteria.MAX_SIZE))));
  }

  @Test
  void filtersByCountry() throws Exception {
    String body = getBody(get("/api/employees").param("countryCode", "IN").param("size", "50"));

    List<String> countries = JsonPath.read(body, "$.content[*].country");
    assertThat(countries).isNotEmpty().containsOnly("India");
  }

  @Test
  void searchesByNameSubstring() throws Exception {
    // Take a real surname from page one, then search for it.
    String firstPage = getBody(get("/api/employees").param("size", "1"));
    String fullName = JsonPath.read(firstPage, "$.content[0].fullName");
    String surname = fullName.split(" ")[1];

    mvc.perform(get("/api/employees").param("search", surname))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements", greaterThan(0)));
  }

  @Test
  void sortsBySalaryUsingTheNormalisedFigureNotRawMinorUnits() throws Exception {
    String body =
        getBody(
            get("/api/employees")
                .param("sort", "salary")
                .param("desc", "true")
                .param("size", "25")
                .param("status", "ACTIVE"));

    List<Number> base = JsonPath.read(body, "$.content[*].currentSalary.annualBase");
    assertThat(base).isNotEmpty();

    // Sorting on raw minor units would rank a mid-level Japanese salary (millions of
    // yen) above every US director. The normalised figure is what makes this correct.
    double previous = Double.MAX_VALUE;
    for (Number value : base) {
      assertThat(value.doubleValue()).isLessThanOrEqualTo(previous);
      previous = value.doubleValue();
    }
  }

  @Test
  void rejectsUnknownSortField() throws Exception {
    mvc.perform(get("/api/employees").param("sort", "salary; DROP TABLE employee"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title", is("Invalid request")));

    // And the table is, of course, still there.
    assertThat(jdbc.queryForObject("SELECT count(*) FROM employee", Integer.class)).isPositive();
  }

  @Test
  void emptyResultIsAnEmptyPageNotAnError() throws Exception {
    mvc.perform(get("/api/employees").param("search", "zzzznobodyzzzz"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.totalElements", is(0)));
  }

  // ---------------------------------------------------------------------- detail

  @Test
  void detailIncludesHistoryAndBandPosition() throws Exception {
    mvc.perform(get("/api/employees/{id}", firstEmployeeId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.history", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$.band.compaRatio", notNullValue()))
        .andExpect(jsonPath("$.band.position", notNullValue()));
  }

  @Test
  void detailNeverExposesGender() throws Exception {
    // Collected for aggregate pay-equity analysis only. See docs/requirements.md.
    mvc.perform(get("/api/employees/{id}", firstEmployeeId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gender").doesNotExist());
  }

  @Test
  void unknownEmployeeIsAProblemDetail() throws Exception {
    mvc.perform(get("/api/employees/{id}", 999_999_999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title", is("Resource not found")))
        .andExpect(jsonPath("$.status", is(404)));
  }

  // ----------------------------------------------------------------------- write

  @Test
  void createsAnEmployeeWithStartingPay() throws Exception {
    mvc.perform(
            post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("grace.hopper@acme.test", "2024-03-01", "195000.00")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currentSalary.amountMinor", is(19_500_000)))
        .andExpect(jsonPath("$.currentSalary.currency", is("USD")))
        .andExpect(jsonPath("$.currentSalary.amount", is(195000.00)))
        .andExpect(jsonPath("$.history", hasSize(1)))
        .andExpect(jsonPath("$.history[0].changeReason", is("INITIAL")));
  }

  @Test
  void rejectsInvalidCreateRequestFieldByField() throws Exception {
    String request =
        """
        {"firstName": "", "lastName": "X", "email": "not-an-email",
         "countryCode": "USA", "jobTitle": "", "startingSalary": -5}
        """;

    mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title", is("Validation failed")))
        .andExpect(jsonPath("$.errors.firstName", notNullValue()))
        .andExpect(jsonPath("$.errors.email", notNullValue()))
        .andExpect(jsonPath("$.errors.startingSalary", notNullValue()));
  }

  @Test
  void recordingARaiseClosesTheOldRecordRatherThanOverwritingIt() throws Exception {
    long id = createEmployee("raise.subject@acme.test", "2023-01-01", "100000.00");

    mvc.perform(
            post("/api/employees/{id}/compensation", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount": 112000.00, "effectiveFrom": "2025-01-01",
                     "changeReason": "PROMOTION", "note": "Promoted to Staff"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.history", hasSize(2)))
        // The original record survives, now closed at the new effective date.
        .andExpect(jsonPath("$.history[0].amount.amountMinor", is(10_000_000)))
        .andExpect(jsonPath("$.history[0].effectiveTo", is("2025-01-01")))
        // The new record is open and carries the movement.
        .andExpect(jsonPath("$.history[1].amount.amountMinor", is(11_200_000)))
        .andExpect(jsonPath("$.history[1].effectiveTo").doesNotExist())
        .andExpect(jsonPath("$.history[1].changePercent", is(12.00)))
        .andExpect(jsonPath("$.currentSalary.amountMinor", is(11_200_000)));
  }

  @Test
  void rejectsABackdatedSalaryChange() throws Exception {
    long id = createEmployee("backdate.subject@acme.test", "2023-06-01", "90000.00");

    mvc.perform(
            post("/api/employees/{id}/compensation", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount": 95000.00, "effectiveFrom": "2022-01-01", "changeReason": "CORRECTION"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title", is("Conflicts with current data")));
  }

  @Test
  void rejectsARaiseToTheSameAmount() throws Exception {
    long id = createEmployee("noop.subject@acme.test", "2023-06-01", "90000.00");

    mvc.perform(
            post("/api/employees/{id}/compensation", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount": 90000.00, "effectiveFrom": "2025-06-01",
                     "changeReason": "ANNUAL_REVIEW"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectsAnUnknownChangeReason() throws Exception {
    long id = createEmployee("reason.subject@acme.test", "2023-06-01", "90000.00");

    mvc.perform(
            post("/api/employees/{id}/compensation", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount": 95000.00, "effectiveFrom": "2025-06-01",
                     "changeReason": "FELT_LIKE_IT"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateChangesAttributesButNotPay() throws Exception {
    long id = createEmployee("update.subject@acme.test", "2023-06-01", "90000.00");

    mvc.perform(
            put("/api/employees/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"firstName": "Renamed", "lastName": "Person",
                     "email": "update.subject@acme.test", "departmentId": %d, "jobLevelId": %d,
                     "jobTitle": "Staff Engineer", "employmentStatus": "ON_LEAVE"}
                    """
                        .formatted(firstDepartmentId(), firstJobLevelId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName", is("Renamed Person")))
        .andExpect(jsonPath("$.employmentStatus", is("ON_LEAVE")))
        // Pay is untouched: it can only move through the compensation endpoint, so a
        // profile edit can never silently rewrite someone's salary.
        .andExpect(jsonPath("$.currentSalary.amountMinor", is(9_000_000)))
        .andExpect(jsonPath("$.history", hasSize(1)));
  }

  @Test
  void rejectsADuplicateEmail() throws Exception {
    createEmployee("duplicate.subject@acme.test", "2023-06-01", "90000.00");

    mvc.perform(
            post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("duplicate.subject@acme.test", "2023-06-01", "90000.00")))
        .andExpect(status().isConflict());
  }

  // ---------------------------------------------------------------------- helpers

  private String getBody(org.springframework.test.web.servlet.RequestBuilder request)
      throws Exception {
    return mvc.perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private long createEmployee(String email, String hireDate, String salary) throws Exception {
    String body =
        mvc.perform(
                post("/api/employees")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody(email, hireDate, salary)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  private String createBody(String email, String hireDate, String salary) {
    return """
        {"firstName": "Test", "lastName": "Subject", "email": "%s",
         "countryCode": "US", "departmentId": %d, "jobLevelId": %d,
         "jobTitle": "Engineer", "hireDate": "%s", "startingSalary": %s}
        """
        .formatted(email, firstDepartmentId(), firstJobLevelId(), hireDate, salary);
  }

  private long firstEmployeeId() throws Exception {
    String body = getBody(get("/api/employees").param("size", "1"));
    return ((Number) JsonPath.read(body, "$.content[0].id")).longValue();
  }

  private long firstDepartmentId() {
    return jdbc.queryForObject("SELECT min(id) FROM department", Long.class);
  }

  private long firstJobLevelId() {
    return jdbc.queryForObject("SELECT min(id) FROM job_level", Long.class);
  }
}
