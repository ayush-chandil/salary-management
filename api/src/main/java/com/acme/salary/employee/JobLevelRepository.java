package com.acme.salary.employee;

import com.acme.salary.domain.JobLevel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobLevelRepository extends JpaRepository<JobLevel, Long> {}
