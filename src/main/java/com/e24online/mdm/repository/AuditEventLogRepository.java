package com.e24online.mdm.repository;

import com.e24online.mdm.domain.AuditEventLog;
import org.springframework.data.repository.CrudRepository;

public interface AuditEventLogRepository extends CrudRepository<AuditEventLog, Long> {
}

