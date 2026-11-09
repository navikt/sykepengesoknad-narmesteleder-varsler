CREATE TABLE narmeste_leder_esyfo
(
    id                           VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    oppdatert                    TIMESTAMP WITH TIME ZONE NOT NULL,
    timestamp                    TIMESTAMP WITH TIME ZONE NOT NULL,
    narmeste_leder_id            UUID NOT NULL UNIQUE,
    orgnummer                    VARCHAR NOT NULL,
    bruker_fnr                   VARCHAR NOT NULL,
    narmeste_leder_fnr           VARCHAR NOT NULL,
    narmeste_leder_telefonnummer VARCHAR NOT NULL,
    narmeste_leder_epost         VARCHAR NOT NULL,
    aktiv_fom                    DATE NOT NULL,
    aktiv_tom                    DATE,
    arbeidsgiver_forskutterer    BOOLEAN
);

CREATE INDEX narmeste_leder_esyfo_aktiv_idx
    ON narmeste_leder_esyfo (bruker_fnr, orgnummer) WHERE aktiv_tom IS NULL;

CREATE TABLE narmeste_leder_esyfo_offset
(
    topic       VARCHAR NOT NULL,
    partition   INTEGER NOT NULL,
    last_offset BIGINT NOT NULL,
    PRIMARY KEY (topic, partition)
);
