CREATE TABLE secrets (
  id bigint NOT NULL AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  updatedat bigint NOT NULL,
  createdat bigint NOT NULL,
  description varchar(255),
  createdby varchar(255),
  updatedby varchar(255),
  type varchar(20),
  options varchar(255) NOT NULL DEFAULT '{}',
  PRIMARY KEY (id),
  UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE groups (
  id bigint NOT NULL AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  updatedat bigint NOT NULL,
  createdat bigint NOT NULL,
  description varchar(255),
  createdby varchar(255),
  updatedby varchar(255),
  PRIMARY KEY (id),
  UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE clients (
  id bigint NOT NULL AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  updatedat bigint NOT NULL,
  createdat bigint NOT NULL,
  description varchar(255),
  createdby varchar(255),
  updatedby varchar(255),
  enabled bool NOT NULL DEFAULT false,
  automationallowed bool NOT NULL DEFAULT false,
  PRIMARY KEY(id),
  UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE accessgrants (
  id bigint NOT NULL AUTO_INCREMENT,
  groupid bigint NOT NULL,
  secretid bigint NOT NULL,
  updatedat bigint NOT NULL,
  createdat bigint NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE memberships (
  id bigint NOT NULL AUTO_INCREMENT,
  groupid bigint NOT NULL,
  clientid bigint NOT NULL,
  updatedat bigint NOT NULL,
  createdat bigint NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE secrets_content (
  id bigint NOT NULL AUTO_INCREMENT,
  secretid bigint NOT NULL,
  version varchar(20),
  updatedat bigint NOT NULL,
  createdat bigint NOT NULL,
  createdby varchar(255),
  updatedby varchar(255),
  encrypted_content longtext NOT NULL,
  metadata text NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (secretid, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE users (
  username varchar(255) NOT NULL,
  password_hash varchar(128) NOT NULL,
  updated_at bigint NOT NULL,
  created_at bigint NOT NULL,
  PRIMARY KEY (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
