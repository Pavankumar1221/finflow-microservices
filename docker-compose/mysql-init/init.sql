-- ================================================================
-- FinFlow MySQL Initialisation Script
-- Runs automatically on first container start (mysql/init/*.sql)
-- Creates all 4 service databases and the application user.
-- ================================================================

-- Create databases (service-per-database pattern)
CREATE DATABASE IF NOT EXISTS `finflow_auth`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `finflow_application`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `finflow_document`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `finflow_admin`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create application user (matches credentials in config-repo YMLs)
CREATE USER IF NOT EXISTS 'pavan'@'%' IDENTIFIED BY 'Pavan@2004';

-- Grant full access to all finflow databases
GRANT ALL PRIVILEGES ON `finflow_auth`.*        TO 'pavan'@'%';
GRANT ALL PRIVILEGES ON `finflow_application`.* TO 'pavan'@'%';
GRANT ALL PRIVILEGES ON `finflow_document`.*    TO 'pavan'@'%';
GRANT ALL PRIVILEGES ON `finflow_admin`.*       TO 'pavan'@'%';

FLUSH PRIVILEGES;
