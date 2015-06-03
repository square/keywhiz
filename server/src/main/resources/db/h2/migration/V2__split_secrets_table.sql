CREATE TABLE secrets_content (
   id SERIAL primary key,
   secretId int REFERENCES secrets ON DELETE CASCADE,
   content text NOT NULL,
   version character varying(20),
   createdAt timestamp NOT NULL DEFAULT NOW(),
   updatedAt timestamp NOT NULL DEFAULT NOW(),
   createdBy text,
   updatedBy text
);

ALTER TABLE secrets ADD COLUMN type character varying(20);
ALTER TABLE secrets ADD COLUMN options text NOT NULL DEFAULT '{}';

CREATE UNIQUE INDEX secrets_content_secretid_version_idx ON secrets_content (secretId, version);

INSERT INTO secrets_content (secretId, content, version, createdAt, updatedAt, createdBy, updatedBy)
  SELECT id, secret, version, createdAt, updatedAt, createdBy, updatedBy FROM secrets;
