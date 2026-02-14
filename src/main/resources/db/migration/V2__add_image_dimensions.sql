-- V2__add_image_dimensions.sql
-- Add image dimension columns for PhotoSwipe lightbox integration.
-- PhotoSwipe requires width/height to display images without layout shift.

ALTER TABLE portfolio_item ADD COLUMN image_width  INT;
ALTER TABLE portfolio_item ADD COLUMN image_height INT;

-- Default existing rows to the optimized image width (1200px) with a 4:3 aspect ratio
UPDATE portfolio_item SET image_width = 1200, image_height = 900
WHERE image_width IS NULL;
