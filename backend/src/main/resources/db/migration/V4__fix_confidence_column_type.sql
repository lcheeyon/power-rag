-- V4: Change interactions.confidence from NUMERIC(5,4) to DOUBLE PRECISION
-- Hibernate 6.6 maps Java Double to float(53)/DOUBLE PRECISION; NUMERIC(5,4) fails schema validation.
ALTER TABLE interactions
    ALTER COLUMN confidence TYPE DOUBLE PRECISION USING confidence::DOUBLE PRECISION;
