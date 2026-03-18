-- Lookup/master tables for normalized domain values.
-- Simplified: one seed dataset drives creation and insert for all lookup tables.

CREATE TEMP TABLE IF NOT EXISTS _mdm_lookup_seed (
  table_name TEXT NOT NULL,
  code TEXT NOT NULL,
  description TEXT NOT NULL
);

TRUNCATE TABLE _mdm_lookup_seed;

INSERT INTO _mdm_lookup_seed (table_name, code, description)
VALUES
  ('lkp_os_type', 'ANDROID', 'Android operating system'),
  ('lkp_os_type', 'IOS', 'Apple iOS operating system'),
  ('lkp_os_type', 'WINDOWS', 'Microsoft Windows operating system'),
  ('lkp_os_type', 'MACOS', 'Apple macOS operating system'),
  ('lkp_os_type', 'LINUX', 'Linux operating system'),
  ('lkp_os_type', 'CHROMEOS', 'Google ChromeOS operating system'),
  ('lkp_os_type', 'FREEBSD', 'FreeBSD operating system'),
  ('lkp_os_type', 'OPENBSD', 'OpenBSD operating system'),

  ('lkp_os_name', 'DEBIAN', 'Debian Linux distribution'),
  ('lkp_os_name', 'CENTOS', 'CentOS Linux distribution'),
  ('lkp_os_name', 'FEDORA', 'Fedora Linux distribution'),
  ('lkp_os_name', 'MINT', 'Linux Mint distribution'),
  ('lkp_os_name', 'LINUXMINT', 'Linux Mint distribution (canonical code)'),
  ('lkp_os_name', 'REDHAT', 'Red Hat Enterprise Linux distribution'),
  ('lkp_os_name', 'RHEL', 'Red Hat Enterprise Linux distribution (canonical code)'),
  ('lkp_os_name', 'UBUNTU', 'Ubuntu Linux distribution'),
  ('lkp_os_name', 'ROCKY', 'Rocky Linux distribution'),
  ('lkp_os_name', 'ALMALINUX', 'AlmaLinux distribution'),
  ('lkp_os_name', 'SUSE', 'SUSE Linux distribution'),
  ('lkp_os_name', 'OPENSUSE', 'openSUSE Linux distribution'),
  ('lkp_os_name', 'ARCH', 'Arch Linux distribution'),
  ('lkp_os_name', 'KALI', 'Kali Linux distribution'),
  ('lkp_os_name', 'OTHER', 'Other Linux distribution'),

  ('lkp_os_platform', 'WINDOWS', 'Microsoft Windows platform'),
  ('lkp_os_platform', 'MACOS', 'Apple macOS platform'),
  ('lkp_os_platform', 'CHROMEOS', 'Google ChromeOS platform'),
  ('lkp_os_platform', 'UBUNTU', 'Ubuntu platform'),
  ('lkp_os_platform', 'DEBIAN', 'Debian platform'),
  ('lkp_os_platform', 'FEDORA', 'Fedora platform'),
  ('lkp_os_platform', 'ARCH', 'Arch Linux platform'),
  ('lkp_os_platform', 'OPENSUSE', 'openSUSE platform'),
  ('lkp_os_platform', 'LINUXMINT', 'Linux Mint platform'),
  ('lkp_os_platform', 'KALI', 'Kali Linux platform'),
  ('lkp_os_platform', 'ANDROID', 'Android platform'),
  ('lkp_os_platform', 'IOS', 'Apple iOS platform'),
  ('lkp_os_platform', 'RHEL', 'Red Hat Enterprise Linux platform'),
  ('lkp_os_platform', 'CENTOS', 'CentOS platform'),
  ('lkp_os_platform', 'ROCKY', 'Rocky Linux platform'),
  ('lkp_os_platform', 'ALMALINUX', 'AlmaLinux platform'),
  ('lkp_os_platform', 'FREEBSD', 'FreeBSD platform'),
  ('lkp_os_platform', 'OPENBSD', 'OpenBSD platform'),

  ('lkp_os_lifecycle_state', 'SUPPORTED', 'Vendor still supports this cycle with no announced EOL date'),
  ('lkp_os_lifecycle_state', 'EOL', 'Cycle has crossed EOL date but may still be in extended support'),
  ('lkp_os_lifecycle_state', 'EEOL', 'Cycle has crossed extended EOL date or final support date'),
  ('lkp_os_lifecycle_state', 'NOT_TRACKED', 'Lifecycle cannot be resolved from catalog'),

  ('lkp_device_type', 'PHONE', 'Phone device class'),
  ('lkp_device_type', 'TABLET', 'Tablet device class'),
  ('lkp_device_type', 'LAPTOP', 'Laptop device class'),
  ('lkp_device_type', 'DESKTOP', 'Desktop device class'),
  ('lkp_device_type', 'IOT', 'Internet of Things device class'),
  ('lkp_device_type', 'SERVER', 'Server device class'),

  ('lkp_record_status', 'ACTIVE', 'Record is active'),
  ('lkp_record_status', 'INACTIVE', 'Record is inactive'),

  ('lkp_match_mode', 'ALL', 'All conditions must match'),
  ('lkp_match_mode', 'ANY', 'Any one condition can match'),

  ('lkp_compliance_action', 'ALLOW', 'Allow normal access'),
  ('lkp_compliance_action', 'NOTIFY', 'Allow with notification'),
  ('lkp_compliance_action', 'QUARANTINE', 'Limit device/network access'),
  ('lkp_compliance_action', 'BLOCK', 'Block access'),

  ('lkp_rule_condition_operator', 'EQ', 'Equals'),
  ('lkp_rule_condition_operator', 'NEQ', 'Not equals'),
  ('lkp_rule_condition_operator', 'GT', 'Greater than'),
  ('lkp_rule_condition_operator', 'GTE', 'Greater than or equal'),
  ('lkp_rule_condition_operator', 'LT', 'Less than'),
  ('lkp_rule_condition_operator', 'LTE', 'Less than or equal'),
  ('lkp_rule_condition_operator', 'IN', 'Contained in set'),
  ('lkp_rule_condition_operator', 'NOT_IN', 'Not contained in set'),
  ('lkp_rule_condition_operator', 'REGEX', 'Regular expression match'),
  ('lkp_rule_condition_operator', 'EXISTS', 'Field exists'),
  ('lkp_rule_condition_operator', 'NOT_EXISTS', 'Field does not exist'),

  ('lkp_signal_source', 'SYSTEM_RULE', 'Signal generated from system-information rule'),
  ('lkp_signal_source', 'REJECT_APPLICATION', 'Signal generated from reject-application policy'),
  ('lkp_signal_source', 'POSTURE_SIGNAL', 'Signal generated from posture detection'),
  ('lkp_signal_source', 'MANUAL', 'Signal generated manually by operator'),

  ('lkp_score_band', 'CRITICAL', 'Critical trust state'),
  ('lkp_score_band', 'HIGH_RISK', 'High risk state'),
  ('lkp_score_band', 'MEDIUM_RISK', 'Medium risk state'),
  ('lkp_score_band', 'LOW_RISK', 'Low risk state'),
  ('lkp_score_band', 'TRUSTED', 'Trusted state'),

  ('lkp_posture_status', 'COMPLIANT', 'Device is compliant'),
  ('lkp_posture_status', 'NON_COMPLIANT', 'Device is non-compliant'),
  ('lkp_posture_status', 'UNKNOWN', 'Compliance state is unknown'),

  ('lkp_payload_process_status', 'RECEIVED', 'Payload received from device'),
  ('lkp_payload_process_status', 'QUEUED', 'Payload accepted and queued for asynchronous evaluation'),
  ('lkp_payload_process_status', 'VALIDATED', 'Payload validated'),
  ('lkp_payload_process_status', 'EVALUATED', 'Payload evaluated'),
  ('lkp_payload_process_status', 'FAILED', 'Payload processing failed'),

  ('lkp_installed_app_status', 'ACTIVE', 'Application currently installed and active'),
  ('lkp_installed_app_status', 'REMOVED', 'Application was removed'),
  ('lkp_installed_app_status', 'UNKNOWN', 'Application state is unknown'),

  ('lkp_remediation_type', 'USER_ACTION', 'User must perform a remediation step'),
  ('lkp_remediation_type', 'AUTO_ACTION', 'System performs remediation automatically'),
  ('lkp_remediation_type', 'NETWORK_RESTRICT', 'Network access restriction remediation'),
  ('lkp_remediation_type', 'APP_REMOVAL', 'Remove a disallowed application'),
  ('lkp_remediation_type', 'OS_UPDATE', 'Update operating system'),
  ('lkp_remediation_type', 'POLICY_ACK', 'User policy acknowledgment required'),

  ('lkp_rule_remediation_source', 'SYSTEM_RULE', 'Mapping from system-information rule'),
  ('lkp_rule_remediation_source', 'REJECT_APPLICATION', 'Mapping from reject-application policy'),
  ('lkp_rule_remediation_source', 'TRUST_POLICY', 'Mapping from trust-score policy'),
  ('lkp_rule_remediation_source', 'DECISION', 'Mapping from final decision action'),

  ('lkp_enforce_mode', 'AUTO', 'Automatically enforce remediation'),
  ('lkp_enforce_mode', 'MANUAL', 'Manual enforcement by operator'),
  ('lkp_enforce_mode', 'ADVISORY', 'Advisory guidance only'),

  ('lkp_evaluation_status', 'IN_PROGRESS', 'Evaluation in progress'),
  ('lkp_evaluation_status', 'COMPLETED', 'Evaluation completed'),
  ('lkp_evaluation_status', 'FAILED', 'Evaluation failed'),

  ('lkp_match_source', 'SYSTEM_RULE', 'Match from system-information rule'),
  ('lkp_match_source', 'REJECT_APPLICATION', 'Match from reject-application policy'),
  ('lkp_match_source', 'TRUST_POLICY', 'Match from trust-score policy'),

  ('lkp_remediation_source', 'MATCH', 'Remediation generated from matched finding'),
  ('lkp_remediation_source', 'DECISION', 'Remediation generated from final decision'),

  ('lkp_remediation_status', 'PENDING', 'Remediation pending'),
  ('lkp_remediation_status', 'SENT', 'Remediation sent'),
  ('lkp_remediation_status', 'ACKED', 'Remediation acknowledged'),
  ('lkp_remediation_status', 'SKIPPED', 'Remediation skipped'),
  ('lkp_remediation_status', 'FAILED', 'Remediation failed'),

  ('lkp_delivery_status', 'PENDING', 'Delivery pending'),
  ('lkp_delivery_status', 'SENT', 'Delivery sent'),
  ('lkp_delivery_status', 'ACKED', 'Delivery acknowledged'),
  ('lkp_delivery_status', 'FAILED', 'Delivery failed'),
  ('lkp_delivery_status', 'TIMEOUT', 'Delivery timed out'),

  ('lkp_app_category', 'VPN_PROXY', 'VPN and proxy applications'),
  ('lkp_app_category', 'ROOT_PRIVILEGE', 'Root or privilege escalation tools'),
  ('lkp_app_category', 'REMOTE_ADMIN', 'Remote administration applications'),
  ('lkp_app_category', 'SIDELOAD_STORE', 'Third-party stores and sideload sources'),
  ('lkp_app_category', 'P2P', 'Peer-to-peer and torrent applications'),
  ('lkp_app_category', 'SPYWARE', 'Spyware applications'),
  ('lkp_app_category', 'ADWARE', 'Adware applications'),
  ('lkp_app_category', 'FAKE_OPTIMIZER', 'Fake optimizer/scam applications'),
  ('lkp_app_category', 'CRYPTO_MINING', 'Cryptomining applications'),
  ('lkp_app_category', 'CHEAT_TOOLS', 'Game cheat and hacking tools'),

  ('lkp_threat_type', 'VPN', 'Virtual private network risk'),
  ('lkp_threat_type', 'ROOT', 'Privilege escalation risk'),
  ('lkp_threat_type', 'RAT', 'Remote administration tool risk'),
  ('lkp_threat_type', 'SIDELOAD', 'Sideload source risk'),
  ('lkp_threat_type', 'TORRENT', 'Peer-to-peer/torrent risk'),
  ('lkp_threat_type', 'SPYWARE', 'Spyware risk'),
  ('lkp_threat_type', 'ADWARE', 'Adware risk'),
  ('lkp_threat_type', 'SCAM', 'Fraud/scam risk'),
  ('lkp_threat_type', 'MINER', 'Cryptomining risk'),
  ('lkp_threat_type', 'CHEAT', 'Cheat/hack risk');

-- Unified lookup master table (single source for all lookup values).
DROP VIEW IF EXISTS lkp_master_value_all;
DROP FUNCTION IF EXISTS mdm_get_lkp_master_values();

CREATE TABLE IF NOT EXISTS lkp_master (
  lookup_type TEXT NOT NULL,
  code TEXT NOT NULL,
  description TEXT NOT NULL,
  PRIMARY KEY (lookup_type, code)
);

CREATE INDEX IF NOT EXISTS idx_lkp_master_code
  ON lkp_master (code);

INSERT INTO lkp_master (lookup_type, code, description)
SELECT
  s.table_name,
  s.code,
  s.description
FROM _mdm_lookup_seed s
ON CONFLICT (lookup_type, code) DO UPDATE
SET description = EXCLUDED.description;

DELETE FROM lkp_master m
WHERE NOT EXISTS (
  SELECT 1
  FROM _mdm_lookup_seed s
  WHERE s.table_name = m.lookup_type
    AND s.code = m.code
);

DROP TABLE IF EXISTS lkp_master_value_all;

-- Drop legacy per-domain lookup tables (lkp_*) and dependent FK constraints.
DO $$
DECLARE
  rec RECORD;
BEGIN
  FOR rec IN
    SELECT t.tablename
    FROM pg_catalog.pg_tables t
    WHERE t.schemaname = 'public'
      AND t.tablename LIKE 'lkp\_%' ESCAPE '\'
      AND t.tablename <> 'lkp_master'
  LOOP
    EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', rec.tablename);
  END LOOP;
END
$$;

-- Normalized catalog for application identity.
CREATE TABLE IF NOT EXISTS application_catalog (
  id BIGSERIAL PRIMARY KEY,
  os_type TEXT NOT NULL,
  package_id TEXT,
  app_name TEXT NOT NULL,
  app_name_norm TEXT GENERATED ALWAYS AS (lower(app_name)) STORED,
  package_id_norm TEXT GENERATED ALWAYS AS (COALESCE(package_id, '')) STORED,
  publisher TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_application_catalog_identity
  ON application_catalog (os_type, package_id_norm, app_name_norm);

CREATE INDEX IF NOT EXISTS idx_application_catalog_lookup
  ON application_catalog (os_type, package_id_norm, app_name_norm);

DROP TABLE IF EXISTS _mdm_lookup_seed;
