#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

: "${PGHOST:=localhost}"
: "${PGPORT:=5432}"
: "${PGDATABASE:=mdm}"
: "${PGUSER:=mdm}"
: "${PGPASSWORD:=mdm}"
export PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD

: "${ROWS:=1000}"
: "${APPLY_SCHEMA:=1}"

# Reject app category ratios (sum=100)
export CAT_VPN_PROXY_PCT="${CAT_VPN_PROXY_PCT:-20}"
export CAT_ROOT_PRIVILEGE_PCT="${CAT_ROOT_PRIVILEGE_PCT:-10}"
export CAT_REMOTE_ADMIN_PCT="${CAT_REMOTE_ADMIN_PCT:-12}"
export CAT_SIDELOAD_STORE_PCT="${CAT_SIDELOAD_STORE_PCT:-10}"
export CAT_P2P_PCT="${CAT_P2P_PCT:-8}"
export CAT_SPYWARE_PCT="${CAT_SPYWARE_PCT:-10}"
export CAT_ADWARE_PCT="${CAT_ADWARE_PCT:-10}"
export CAT_FAKE_OPTIMIZER_PCT="${CAT_FAKE_OPTIMIZER_PCT:-8}"
export CAT_CRYPTO_MINING_PCT="${CAT_CRYPTO_MINING_PCT:-4}"
export CAT_CHEAT_TOOLS_PCT="${CAT_CHEAT_TOOLS_PCT:-8}"

# Reject app OS ratios (sum=100)
export REJECT_OS_ANDROID_PCT="${REJECT_OS_ANDROID_PCT:-60}"
export REJECT_OS_WINDOWS_PCT="${REJECT_OS_WINDOWS_PCT:-25}"
export REJECT_OS_IOS_PCT="${REJECT_OS_IOS_PCT:-10}"
export REJECT_OS_MACOS_PCT="${REJECT_OS_MACOS_PCT:-3}"
export REJECT_OS_LINUX_PCT="${REJECT_OS_LINUX_PCT:-2}"
export REJECT_OS_CHROMEOS_PCT="${REJECT_OS_CHROMEOS_PCT:-0}"
export REJECT_OS_FREEBSD_PCT="${REJECT_OS_FREEBSD_PCT:-0}"
export REJECT_OS_OPENBSD_PCT="${REJECT_OS_OPENBSD_PCT:-0}"

# System rules OS ratios (sum=100)
export SYS_OS_ANDROID_PCT="${SYS_OS_ANDROID_PCT:-55}"
export SYS_OS_WINDOWS_PCT="${SYS_OS_WINDOWS_PCT:-25}"
export SYS_OS_IOS_PCT="${SYS_OS_IOS_PCT:-10}"
export SYS_OS_MACOS_PCT="${SYS_OS_MACOS_PCT:-5}"
export SYS_OS_LINUX_PCT="${SYS_OS_LINUX_PCT:-5}"
export SYS_OS_CHROMEOS_PCT="${SYS_OS_CHROMEOS_PCT:-0}"
export SYS_OS_FREEBSD_PCT="${SYS_OS_FREEBSD_PCT:-0}"
export SYS_OS_OPENBSD_PCT="${SYS_OS_OPENBSD_PCT:-0}"

# System rules device_type ratios (sum=100)
export SYS_DEV_PHONE_PCT="${SYS_DEV_PHONE_PCT:-40}"
export SYS_DEV_TABLET_PCT="${SYS_DEV_TABLET_PCT:-10}"
export SYS_DEV_LAPTOP_PCT="${SYS_DEV_LAPTOP_PCT:-25}"
export SYS_DEV_DESKTOP_PCT="${SYS_DEV_DESKTOP_PCT:-15}"
export SYS_DEV_IOT_PCT="${SYS_DEV_IOT_PCT:-5}"
export SYS_DEV_SERVER_PCT="${SYS_DEV_SERVER_PCT:-5}"

echo "DB: ${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE}"
echo "Rows per table: ${ROWS}"

# Auto-detect psql.exe on Git Bash Windows
if ! command -v psql >/dev/null 2>&1; then
  for v in 18 17 16 15 14 13 12; do
    CANDIDATE="/c/Program Files/PostgreSQL/${v}/bin/psql.exe"
    if [ -x "$CANDIDATE" ]; then
      echo "psql not in PATH; using: $CANDIDATE"
      PSQL="$CANDIDATE"
      break
    fi
  done
else
  PSQL="psql"
fi

if [ -z "${PSQL:-}" ]; then
  echo "ERROR: psql not found. Install PostgreSQL client or add psql to PATH."
  exit 1
fi

if [ "${APPLY_SCHEMA}" = "1" ]; then
  echo "Applying latest schema: ${SCRIPT_DIR}/sql/00_apply_all.sql"
  "$PSQL" -v ON_ERROR_STOP=1 -f "${SCRIPT_DIR}/sql/00_apply_all.sql"
fi

# IMPORTANT: values are passed as psql vars; SQL uses :'var' (not ${VAR})
"$PSQL" -v ON_ERROR_STOP=1 \
  -v rows="$ROWS" \
  -v cat_vpn="$CAT_VPN_PROXY_PCT" \
  -v cat_root="$CAT_ROOT_PRIVILEGE_PCT" \
  -v cat_remote="$CAT_REMOTE_ADMIN_PCT" \
  -v cat_sideload="$CAT_SIDELOAD_STORE_PCT" \
  -v cat_p2p="$CAT_P2P_PCT" \
  -v cat_spy="$CAT_SPYWARE_PCT" \
  -v cat_ad="$CAT_ADWARE_PCT" \
  -v cat_fake="$CAT_FAKE_OPTIMIZER_PCT" \
  -v cat_miner="$CAT_CRYPTO_MINING_PCT" \
  -v cat_cheat="$CAT_CHEAT_TOOLS_PCT" \
  -v rej_android="$REJECT_OS_ANDROID_PCT" \
  -v rej_windows="$REJECT_OS_WINDOWS_PCT" \
  -v rej_ios="$REJECT_OS_IOS_PCT" \
  -v rej_macos="$REJECT_OS_MACOS_PCT" \
  -v rej_linux="$REJECT_OS_LINUX_PCT" \
  -v rej_chromeos="$REJECT_OS_CHROMEOS_PCT" \
  -v rej_freebsd="$REJECT_OS_FREEBSD_PCT" \
  -v rej_openbsd="$REJECT_OS_OPENBSD_PCT" \
  -v sys_android="$SYS_OS_ANDROID_PCT" \
  -v sys_windows="$SYS_OS_WINDOWS_PCT" \
  -v sys_ios="$SYS_OS_IOS_PCT" \
  -v sys_macos="$SYS_OS_MACOS_PCT" \
  -v sys_linux="$SYS_OS_LINUX_PCT" \
  -v sys_chromeos="$SYS_OS_CHROMEOS_PCT" \
  -v sys_freebsd="$SYS_OS_FREEBSD_PCT" \
  -v sys_openbsd="$SYS_OS_OPENBSD_PCT" \
  -v dev_phone="$SYS_DEV_PHONE_PCT" \
  -v dev_tablet="$SYS_DEV_TABLET_PCT" \
  -v dev_laptop="$SYS_DEV_LAPTOP_PCT" \
  -v dev_desktop="$SYS_DEV_DESKTOP_PCT" \
  -v dev_iot="$SYS_DEV_IOT_PCT" \
  -v dev_server="$SYS_DEV_SERVER_PCT" \
<<'SQL'
\set VERBOSITY verbose
\set SHOW_CONTEXT always

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- md5 hex -> byte (0..255), wraps pos into 1..31.
CREATE OR REPLACE FUNCTION _md5byte(h text, pos int)
RETURNS int
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT get_byte(
           decode(substr(h, ((pos - 1) % 31) + 1, 2), 'hex'),
           0
         );
$$;

-- ============================================================
-- Seed reject_application_list (STRICT category + STRICT OS)
-- ============================================================
TRUNCATE TABLE reject_application_list RESTART IDENTITY CASCADE;

WITH
cfg AS (SELECT (:'rows')::int AS total_rows),

cat_ratios AS (
  SELECT * FROM (VALUES
    ('VPN_PROXY'::text,       (:'cat_vpn')::int,    'POLICY_VPN_BLOCK'::text,        'VPN'::text,     4::smallint, 'Unauthorized VPN/Proxy usage'),
    ('ROOT_PRIVILEGE',        (:'cat_root')::int,   'POLICY_ROOT_BLOCK'::text,       'ROOT'::text,    5::smallint, 'Root/privilege tool detected'),
    ('REMOTE_ADMIN',          (:'cat_remote')::int, 'POLICY_REMOTE_ADMIN_BLOCK'::text,'RAT'::text,    4::smallint, 'Unapproved remote administration tool'),
    ('SIDELOAD_STORE',        (:'cat_sideload')::int,'POLICY_SIDELOAD_STORE_BLOCK'::text,'SIDELOAD'::text,4::smallint,'Third-party app store / sideload source'),
    ('P2P',                   (:'cat_p2p')::int,    'POLICY_P2P_BLOCK'::text,        'TORRENT'::text, 3::smallint, 'P2P/torrent application'),
    ('SPYWARE',               (:'cat_spy')::int,    'POLICY_SPYWARE_BLOCK'::text,    'SPYWARE'::text, 5::smallint, 'Spyware / monitoring risk'),
    ('ADWARE',                (:'cat_ad')::int,     'POLICY_ADWARE_BLOCK'::text,     'ADWARE'::text,  3::smallint, 'Adware / intrusive ads risk'),
    ('FAKE_OPTIMIZER',        (:'cat_fake')::int,   'POLICY_FAKE_OPTIMIZER_BLOCK'::text,'SCAM'::text, 3::smallint, 'Fake optimizer / scam risk'),
    ('CRYPTO_MINING',         (:'cat_miner')::int,  'POLICY_MINER_BLOCK'::text,      'MINER'::text,   5::smallint, 'Cryptomining / resource abuse'),
    ('CHEAT_TOOLS',           (:'cat_cheat')::int,  'POLICY_CHEAT_TOOLS_BLOCK'::text,'CHEAT'::text,   3::smallint, 'Cheat/hack tools often bundle malware')
  ) v(app_category, pct, policy_tag, threat_type, severity, reason)
),

name_catalog AS (
  SELECT * FROM (VALUES
    ('VPN_PROXY',      ARRAY['NordVPN','ExpressVPN','Proton VPN','Surfshark','CyberGhost','Private Internet Access','TunnelBear','Hotspot Shield','Windscribe','IPVanish','Mullvad VPN','PureVPN']::text[]),
    ('ROOT_PRIVILEGE', ARRAY['Magisk','SuperSU','KingRoot','KingoRoot','Framaroot','One Click Root','Root Checker (Basic)','BusyBox','Xposed Installer','Lucky Patcher']::text[]),
    ('REMOTE_ADMIN',   ARRAY['AnyDesk','TeamViewer','Chrome Remote Desktop','Splashtop','LogMeIn','RemotePC','VNC Viewer','Zoho Assist','RealVNC Viewer','UltraVNC']::text[]),
    ('SIDELOAD_STORE', ARRAY['APKPure','Aptoide','APKMirror','Aurora Store','F-Droid','Uptodown','GetJar','ACMarket','9Apps','AppBrain']::text[]),
    ('P2P',            ARRAY['uTorrent','BitTorrent','qBittorrent','Vuze','FrostWire','Deluge','Transmission','Tixati','BitComet','WebTorrent']::text[]),
    ('SPYWARE',        ARRAY['SpyNote','DroidJack','AhMyth RAT','AndroRAT','Keylogger Pro','TrackView','mSpy','FlexiSPY','Hoverwatch','iKeyMonitor']::text[]),
    ('ADWARE',         ARRAY['Adware Cleaner','PushAd Notifier','Flash Deals','Coupon Rush','Lucky Ads','News Buzz','Weather Fast','Deal Storm','AdPopper','Promo Hub']::text[]),
    ('FAKE_OPTIMIZER', ARRAY['Clean Master','DU Speed Booster','Battery Saver','RAM Booster','Fast Cleaner','Junk Cleaner','Phone Cooler','Memory Doctor','Smart Booster','Device Optimizer']::text[]),
    ('CRYPTO_MINING',  ARRAY['CryptoTab Browser','MinerGate','NiceHash','Crypto Miner','Hash Miner','Coin Miner','Mining Pro','Hash Boost']::text[]),
    ('CHEAT_TOOLS',    ARRAY['GameGuardian','Cheat Engine','Mod Menu','Aimbot X','WallHack','SpeedHack','Trainer Pro','Hack Tools','GG Toolkit','Script Injector']::text[])
  ) v(app_category, names)
),

os_ratios AS (
  SELECT * FROM (VALUES
    ('ANDROID'::text, (:'rej_android')::int),
    ('WINDOWS'::text, (:'rej_windows')::int),
    ('IOS'::text,     (:'rej_ios')::int),
    ('MACOS'::text,   (:'rej_macos')::int),
    ('LINUX'::text,   (:'rej_linux')::int),
    ('CHROMEOS'::text,(:'rej_chromeos')::int),
    ('FREEBSD'::text, (:'rej_freebsd')::int),
    ('OPENBSD'::text, (:'rej_openbsd')::int)
  ) v(app_os_type, pct)
),

cat_counts AS (
  SELECT
    app_category, pct, policy_tag, threat_type, severity, reason,
    CASE
      WHEN app_category <> (SELECT app_category FROM cat_ratios ORDER BY app_category DESC LIMIT 1)
        THEN floor((c.total_rows * pct)::numeric / 100)::int
      ELSE c.total_rows - (
        SELECT sum(floor((c.total_rows * pct)::numeric / 100)::int)
        FROM cat_ratios
        WHERE app_category <> (SELECT app_category FROM cat_ratios ORDER BY app_category DESC LIMIT 1)
      )
    END AS target_count
  FROM cat_ratios CROSS JOIN cfg c
),
cat_ranges AS (
  SELECT
    app_category, policy_tag, threat_type, severity, reason,
    target_count,
    sum(target_count) OVER (ORDER BY app_category) - target_count + 1 AS start_n,
    sum(target_count) OVER (ORDER BY app_category) AS end_n
  FROM cat_counts
),

os_counts AS (
  SELECT
    app_os_type, pct,
    CASE
      WHEN app_os_type <> (SELECT app_os_type FROM os_ratios ORDER BY app_os_type DESC LIMIT 1)
        THEN floor((c.total_rows * pct)::numeric / 100)::int
      ELSE c.total_rows - (
        SELECT sum(floor((c.total_rows * pct)::numeric / 100)::int)
        FROM os_ratios
        WHERE app_os_type <> (SELECT app_os_type FROM os_ratios ORDER BY app_os_type DESC LIMIT 1)
      )
    END AS target_count
  FROM os_ratios CROSS JOIN cfg c
),
os_ranges AS (
  SELECT
    app_os_type,
    target_count,
    sum(target_count) OVER (ORDER BY app_os_type) - target_count + 1 AS start_n,
    sum(target_count) OVER (ORDER BY app_os_type) AS end_n
  FROM os_counts
),

base AS (
  SELECT gs AS n, md5(gs::text) AS h
  FROM generate_series(1, (SELECT total_rows FROM cfg)) gs
),

os_assigned AS (
  SELECT b.n, b.h, r.app_os_type
  FROM base b JOIN os_ranges r ON b.n BETWEEN r.start_n AND r.end_n
),
cat_assigned AS (
  SELECT b.n, r.app_category, r.policy_tag, r.threat_type, r.severity, r.reason
  FROM base b JOIN cat_ranges r ON b.n BETWEEN r.start_n AND r.end_n
),

picked AS (
  SELECT
    o.n, o.h, o.app_os_type,
    c.app_category, c.policy_tag, c.threat_type, c.severity, c.reason,
    nc.names
  FROM os_assigned o
  JOIN cat_assigned c USING (n)
  JOIN name_catalog nc USING (app_category)
)

INSERT INTO reject_application_list
(policy_tag, threat_type, severity, blocked_reason,
 app_name, publisher, package_id, app_category, app_os_type,
 app_latest_version, min_allowed_version,
 latest_ver_major, latest_ver_minor, latest_ver_patch,
 min_ver_major, min_ver_minor, min_ver_patch,
 status, effective_from, effective_to, is_deleted,
 created_at, created_by, modified_at, modified_by)
SELECT
  policy_tag,
  threat_type,
  severity,
  reason || ' (policy=' || policy_tag || ')' AS blocked_reason,

  names[1 + (_md5byte(h, 1) % array_length(names,1))] AS app_name,

  (ARRAY['Vendor Unknown','Acme Labs','QuickTools Ltd','BlueNova Inc','CloudAssist Co','NetShield'])
    [1 + (_md5byte(h, 3) % 6)] AS publisher,

  CASE
    WHEN app_os_type='ANDROID' THEN 'com.demo.' || lower(regexp_replace(app_category, '[^a-zA-Z0-9]+', '', 'g')) || '.' || lpad(n::text, 8, '0')
    WHEN app_os_type='IOS'     THEN 'com.demo.ios.' || lpad(n::text, 8, '0')
    WHEN app_os_type='WINDOWS' THEN 'win.demo.app.' || lpad(n::text, 8, '0')
    WHEN app_os_type='MACOS'   THEN 'mac.demo.app.' || lpad(n::text, 8, '0')
    WHEN app_os_type='CHROMEOS' THEN 'chromeos.demo.app.' || lpad(n::text, 8, '0')
    WHEN app_os_type='FREEBSD'  THEN 'freebsd.demo.app.' || lpad(n::text, 8, '0')
    WHEN app_os_type='OPENBSD'  THEN 'openbsd.demo.app.' || lpad(n::text, 8, '0')
    ELSE 'linux.demo.app.' || lpad(n::text, 8, '0')
  END AS package_id,

  app_category,
  app_os_type,

  (1 + (_md5byte(h, 5) % 12))::text || '.' ||
  (_md5byte(h, 7) % 30)::text || '.' ||
  (_md5byte(h, 9) % 80)::text AS app_latest_version,

  GREATEST(1, (1 + (_md5byte(h, 5) % 12)) - (_md5byte(h, 11) % 3))::text || '.' ||
  (_md5byte(h, 7) % 30)::text || '.' ||
  (_md5byte(h, 13) % 60)::text AS min_allowed_version,

  (1 + (_md5byte(h, 5) % 12))::int,
  (_md5byte(h, 7) % 30)::int,
  (_md5byte(h, 9) % 80)::int,

  GREATEST(1, (1 + (_md5byte(h, 5) % 12)) - (_md5byte(h, 11) % 3))::int,
  (_md5byte(h, 7) % 30)::int,
  (_md5byte(h, 13) % 60)::int,

  CASE
    WHEN app_category IN ('SPYWARE','ROOT_PRIVILEGE','CRYPTO_MINING') THEN 'ACTIVE'
    WHEN (_md5byte(h, 15) % 100) < 85 THEN 'ACTIVE'
    ELSE 'INACTIVE'
  END AS status,

  now() - ((_md5byte(h, 17) % 90)::text || ' days')::interval AS effective_from,
  NULL::timestamptz AS effective_to,
  false AS is_deleted,

  now() - ((_md5byte(h, 19) % 365)::text || ' days')::interval AS created_at,
  (ARRAY['admin@mdm','secops@mdm','policy@mdm','compliance@mdm','system@mdm'])
    [1 + (_md5byte(h, 21) % 5)] AS created_by,

  now() - ((_md5byte(h, 23) % 120)::text || ' days')::interval AS modified_at,
  (ARRAY['admin@mdm','secops@mdm','policy@mdm','compliance@mdm','system@mdm'])
    [1 + (_md5byte(h, 25) % 5)] AS modified_by
FROM picked;

SELECT app_category, COUNT(*) FROM reject_application_list GROUP BY 1 ORDER BY 1;
SELECT app_os_type,  COUNT(*) FROM reject_application_list GROUP BY 1 ORDER BY 1;

-- ============================================================
-- Seed system_information_rule (STRICT OS + STRICT device_type)
-- ============================================================
TRUNCATE TABLE system_information_rule RESTART IDENTITY CASCADE;

WITH
cfg AS (SELECT (:'rows')::int AS total_rows),

sys_os_ratios AS (
  SELECT * FROM (VALUES
    ('ANDROID'::text, (:'sys_android')::int),
    ('WINDOWS'::text, (:'sys_windows')::int),
    ('IOS'::text,     (:'sys_ios')::int),
    ('MACOS'::text,   (:'sys_macos')::int),
    ('LINUX'::text,   (:'sys_linux')::int),
    ('CHROMEOS'::text,(:'sys_chromeos')::int),
    ('FREEBSD'::text, (:'sys_freebsd')::int),
    ('OPENBSD'::text, (:'sys_openbsd')::int)
  ) v(os_type, pct)
),
sys_dev_ratios AS (
  SELECT * FROM (VALUES
    ('PHONE'::text,   (:'dev_phone')::int),
    ('TABLET'::text,  (:'dev_tablet')::int),
    ('LAPTOP'::text,  (:'dev_laptop')::int),
    ('DESKTOP'::text, (:'dev_desktop')::int),
    ('IOT'::text,     (:'dev_iot')::int),
    ('SERVER'::text,  (:'dev_server')::int)
  ) v(device_type, pct)
),

os_counts AS (
  SELECT os_type, pct,
    CASE
      WHEN os_type <> (SELECT os_type FROM sys_os_ratios ORDER BY os_type DESC LIMIT 1)
        THEN floor((c.total_rows * pct)::numeric / 100)::int
      ELSE c.total_rows - (
        SELECT sum(floor((c.total_rows * pct)::numeric / 100)::int)
        FROM sys_os_ratios
        WHERE os_type <> (SELECT os_type FROM sys_os_ratios ORDER BY os_type DESC LIMIT 1)
      )
    END AS target_count
  FROM sys_os_ratios CROSS JOIN cfg c
),
os_ranges AS (
  SELECT os_type, target_count,
    sum(target_count) OVER (ORDER BY os_type) - target_count + 1 AS start_n,
    sum(target_count) OVER (ORDER BY os_type) AS end_n
  FROM os_counts
),

dev_counts AS (
  SELECT device_type, pct,
    CASE
      WHEN device_type <> (SELECT device_type FROM sys_dev_ratios ORDER BY device_type DESC LIMIT 1)
        THEN floor((c.total_rows * pct)::numeric / 100)::int
      ELSE c.total_rows - (
        SELECT sum(floor((c.total_rows * pct)::numeric / 100)::int)
        FROM sys_dev_ratios
        WHERE device_type <> (SELECT device_type FROM sys_dev_ratios ORDER BY device_type DESC LIMIT 1)
      )
    END AS target_count
  FROM sys_dev_ratios CROSS JOIN cfg c
),
dev_ranges AS (
  SELECT device_type, target_count,
    sum(target_count) OVER (ORDER BY device_type) - target_count + 1 AS start_n,
    sum(target_count) OVER (ORDER BY device_type) AS end_n
  FROM dev_counts
),

base AS (
  SELECT gs AS n, md5(gs::text || ':sys') AS h
  FROM generate_series(1, (SELECT total_rows FROM cfg)) gs
),

os_assigned AS (
  SELECT b.n, b.h, r.os_type
  FROM base b JOIN os_ranges r ON b.n BETWEEN r.start_n AND r.end_n
),
dev_assigned AS (
  SELECT b.n, r.device_type
  FROM base b JOIN dev_ranges r ON b.n BETWEEN r.start_n AND r.end_n
),

combined AS (
  SELECT o.n, o.h, o.os_type, d.device_type
  FROM os_assigned o JOIN dev_assigned d USING (n)
),

action_pick AS (
  SELECT
    c.*,
    CASE
      WHEN (_md5byte(h, 7) % 100) < 50 THEN 'ALLOW'
      WHEN (_md5byte(h, 7) % 100) < 75 THEN 'NOTIFY'
      WHEN (_md5byte(h, 7) % 100) < 92 THEN 'QUARANTINE'
      ELSE 'BLOCK'
    END AS act
  FROM combined c
)

INSERT INTO system_information_rule
(rule_code, priority, version, match_mode, compliance_action, risk_score_delta,
 rule_tag, status, severity, description,
 device_type, os_type, os_name, os_version, time_zone, kernel_version, apiLevel, osBuildNumber, manufacturer,
 rootDetected, runningOnEmulator, usb_debigging_status,
 effective_from, effective_to, is_deleted,
 created_at, created_by, modified_at, modified_by)
SELECT
  'SYSR_' || lpad(n::text, 10, '0') AS rule_code,
  (1 + (_md5byte(h, 1) % 500))::int,
  (1 + (_md5byte(h, 3) % 5))::int,
  CASE WHEN (_md5byte(h, 5) % 100) < 70 THEN 'ALL' ELSE 'ANY' END,
  act,

  CASE
    WHEN act='ALLOW'      THEN -((_md5byte(h, 9) % 10))::smallint
    WHEN act='NOTIFY'     THEN  ((_md5byte(h,11) % 10))::smallint
    WHEN act='QUARANTINE' THEN  ((_md5byte(h,13) % 50))::smallint
    ELSE                  ((_md5byte(h,15) % 80))::smallint
  END,

  -- Keep tag stable for grouping, uniqueness handled by rule_code
  'RULE_BASELINE_' || os_type || '_' || to_char(now(), 'YYYY') || '_Q' || (1 + (_md5byte(h, 17) % 4))::text,
  CASE WHEN (_md5byte(h, 19) % 100) < 90 THEN 'ACTIVE' ELSE 'INACTIVE' END,
  (1 + (_md5byte(h, 21) % 5))::smallint,
  'Strict baseline rule seed for ' || os_type || ' / ' || device_type,

  device_type,
  os_type,
  CASE
    WHEN os_type='LINUX' THEN
      (ARRAY['DEBIAN','CENTOS','FEDORA','LINUXMINT','RHEL','UBUNTU','ROCKY','ALMALINUX','OPENSUSE','ARCH','KALI','OTHER'])[1 + (_md5byte(h, 22) % 12)]
    ELSE NULL
  END,

  CASE os_type
    WHEN 'ANDROID' THEN (ARRAY['10','11','12','13','14'])[1 + (_md5byte(h, 23) % 5)]
    WHEN 'IOS'     THEN (ARRAY['15.8.2','16.7.5','17.2.1','17.3.1','17.4.0'])[1 + (_md5byte(h, 23) % 5)]
    WHEN 'WINDOWS' THEN (ARRAY['10 22H2','11 22H2','11 23H2'])[1 + (_md5byte(h, 23) % 3)]
    WHEN 'MACOS'   THEN (ARRAY['Ventura 13.6','Sonoma 14.1','Sonoma 14.2','Sonoma 14.3'])[1 + (_md5byte(h, 23) % 4)]
    WHEN 'CHROMEOS' THEN (ARRAY['126','127','128','129'])[1 + (_md5byte(h, 23) % 4)]
    WHEN 'FREEBSD' THEN (ARRAY['13.5','14.2','14.3','15.0'])[1 + (_md5byte(h, 23) % 4)]
    WHEN 'OPENBSD' THEN (ARRAY['7.5','7.6','7.7','7.8'])[1 + (_md5byte(h, 23) % 4)]
    ELSE                (ARRAY['Ubuntu 22.04','Ubuntu 24.04','Debian 12','Rocky 9.4'])[1 + (_md5byte(h, 23) % 4)]
  END,

  (ARRAY['Asia/Kolkata','Asia/Dubai','Europe/London','America/New_York','Asia/Singapore','Europe/Berlin'])
    [1 + (_md5byte(h, 25) % 6)],

  CASE os_type
    WHEN 'ANDROID' THEN (ARRAY['5.10.107-android','5.10.149-android','5.15.41-android','5.15.74-android','6.1.12-android'])[1 + (_md5byte(h, 27) % 5)]
    WHEN 'WINDOWS' THEN (ARRAY['10.0.19045','10.0.22000','10.0.22631'])[1 + (_md5byte(h, 27) % 3)]
    WHEN 'MACOS'   THEN NULL
    WHEN 'IOS'     THEN NULL
    WHEN 'CHROMEOS' THEN (ARRAY['6.6.36-chromeos','6.6.45-chromeos','6.6.52-chromeos'])[1 + (_md5byte(h, 27) % 3)]
    WHEN 'FREEBSD'  THEN (ARRAY['13.5-RELEASE','14.2-RELEASE','14.3-RELEASE'])[1 + (_md5byte(h, 27) % 3)]
    WHEN 'OPENBSD'  THEN (ARRAY['7.5','7.6','7.7','7.8'])[1 + (_md5byte(h, 27) % 4)]
    ELSE (ARRAY['5.15.0-84-generic','6.1.0-17-generic','6.5.0-26-generic','6.8.0-31-generic'])[1 + (_md5byte(h, 27) % 4)]
  END,

  CASE os_type
    WHEN 'ANDROID' THEN (ARRAY[29,30,31,32,33,34])[1 + (_md5byte(h, 31) % 6)]
    ELSE NULL
  END,

  CASE os_type
    WHEN 'ANDROID' THEN 'UP' || ((_md5byte(h,33) % 10))::text || 'A.' ||
      lpad((_md5byte(h,35))::text, 3, '0') || lpad((_md5byte(h,37))::text, 3, '0') ||
      '.' || lpad(((_md5byte(h,39) % 1000))::text, 3, '0')
    WHEN 'IOS' THEN (ARRAY['20A','20B','20C','21A','21B','21C','22A','22B','22C'])[1 + (_md5byte(h,33) % 9)] ||
      lpad(((_md5byte(h,35) % 100))::text, 2, '0')
    WHEN 'WINDOWS' THEN (ARRAY['19045','22000','22631'])[1 + (_md5byte(h,33) % 3)] || '.' ||
      lpad((_md5byte(h,35))::text, 4, '0') || '.' || lpad((_md5byte(h,37))::text, 2, '0')
    WHEN 'MACOS' THEN (ARRAY['23A','23B','23C','24A','24B','24C'])[1 + (_md5byte(h,33) % 6)] ||
      lpad(((_md5byte(h,35) % 100))::text, 2, '0')
    WHEN 'CHROMEOS' THEN 'R' || (120 + (_md5byte(h,33) % 12))::text || '-' ||
      lpad((_md5byte(h,35))::text, 5, '0') || '.' || lpad((_md5byte(h,37))::text, 2, '0')
    WHEN 'FREEBSD' THEN (ARRAY['13','14','15'])[1 + (_md5byte(h,33) % 3)] || '.' ||
      ((_md5byte(h,35) % 10))::text || '-p' || ((_md5byte(h,37) % 20))::text
    WHEN 'OPENBSD' THEN (ARRAY['7.5','7.6','7.7','7.8'])[1 + (_md5byte(h,33) % 4)] || '-stable'
    ELSE (ARRAY['6.1.0','6.8.0','5.15.0'])[1 + (_md5byte(h,33) % 3)] || '-' || lpad((_md5byte(h,35))::text, 3, '0')
  END,

  CASE os_type
    WHEN 'ANDROID' THEN (ARRAY['Samsung','Xiaomi','OPPO','Vivo','OnePlus','Realme','Motorola'])[1 + (_md5byte(h, 39) % 7)]
    WHEN 'IOS'     THEN 'Apple'
    WHEN 'WINDOWS' THEN (ARRAY['Dell','HP','Lenovo','Acer','Asus','Microsoft'])[1 + (_md5byte(h, 39) % 6)]
    WHEN 'MACOS'   THEN 'Apple'
    WHEN 'CHROMEOS' THEN (ARRAY['Google','Acer','HP','Lenovo','Asus'])[1 + (_md5byte(h, 39) % 5)]
    WHEN 'FREEBSD'  THEN (ARRAY['Netgate','Supermicro','Dell','HP','Custom'])[1 + (_md5byte(h, 39) % 5)]
    WHEN 'OPENBSD'  THEN (ARRAY['Lenovo','Dell','HP','Framework','Custom'])[1 + (_md5byte(h, 39) % 5)]
    ELSE                (ARRAY['Lenovo','Dell','HP','Supermicro','Custom'])[1 + (_md5byte(h, 39) % 5)]
  END,

  CASE WHEN os_type='ANDROID' AND (_md5byte(h, 41) % 100) < 15 THEN true ELSE false END,
  CASE WHEN os_type='ANDROID' AND (_md5byte(h, 43) % 100) < 8  THEN true ELSE false END,
  CASE WHEN os_type='ANDROID' AND (_md5byte(h, 45) % 100) < 20 THEN true ELSE false END,

  now() - ((_md5byte(h, 47) % 120)::text || ' days')::interval,
  NULL::timestamptz,
  false,

  now() - ((_md5byte(h, 49) % 365)::text || ' days')::interval,
  (ARRAY['admin@mdm','secops@mdm','policy@mdm','compliance@mdm','system@mdm'])[1 + (_md5byte(h, 51) % 5)],
  now() - ((_md5byte(h, 53) % 120)::text || ' days')::interval,
  (ARRAY['admin@mdm','secops@mdm','policy@mdm','compliance@mdm','system@mdm'])[1 + (_md5byte(h, 55) % 5)]
FROM action_pick;

SELECT os_type, COUNT(*) FROM system_information_rule GROUP BY 1 ORDER BY 1;
SELECT device_type, COUNT(*) FROM system_information_rule GROUP BY 1 ORDER BY 1;

SELECT 'reject_application_list' AS table, COUNT(*) AS rows FROM reject_application_list
UNION ALL
SELECT 'system_information_rule' AS table, COUNT(*) AS rows FROM system_information_rule;

VACUUM (ANALYZE) reject_application_list;
VACUUM (ANALYZE) system_information_rule;
SQL

echo "Done."
