-- V3__add_site_taglines_and_commissions.sql
-- Add rotating taglines and commissions email for the redesigned static site.

ALTER TABLE site_config ADD COLUMN site_taglines TEXT;
ALTER TABLE site_config ADD COLUMN commissions_email VARCHAR(255);

UPDATE site_config SET site_taglines = 'Illustrator,Digital Designer,Comic Artist'
WHERE site_taglines IS NULL;
