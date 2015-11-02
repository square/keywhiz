CREATE TABLE secrets (
  id int NOT NULL AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  updatedat timestamp NOT NULL DEFAULT now(),
  createdat timestamp NOT NULL DEFAULT now(),
  description varchar(255),
  createdby varchar(255),
  updatedby varchar(255),
  type varchar(20),
  options varchar(255) NOT NULL DEFAULT '{}',
  PRIMARY KEY (id),
  UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE groups (
  id int NOT NULL AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  updatedat timestamp NOT NULL DEFAULT now(),
  createdat timestamp NOT NULL DEFAULT now(),
  description varchar(255),
  createdby varchar(255),
  updatedby varchar(255),
  PRIMARY KEY (id),
  UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE clients (
  id int NOT NULL AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  updatedat timestamp NOT NULL DEFAULT now(),
  createdat timestamp NOT NULL DEFAULT now(),
  description varchar(255),
  createdby varchar(255),
  updatedby varchar(255),
  enabled bool NOT NULL DEFAULT false,
  automationallowed bool NOT NULL DEFAULT false,
  PRIMARY KEY(id),
  UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE accessgrants (
  id int NOT NULL AUTO_INCREMENT,
  groupid int REFERENCES groups(id) ON DELETE CASCADE,
  secretid int REFERENCES secrets(id) ON DELETE CASCADE,
  updatedat timestamp NOT NULL DEFAULT now(),
  createdat timestamp NOT NULL DEFAULT now(),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE memberships (
  id int NOT NULL AUTO_INCREMENT,
  groupid int REFERENCES groups(id) ON DELETE CASCADE,
  clientid int REFERENCES clients(id) ON DELETE CASCADE,
  updatedat timestamp NOT NULL DEFAULT now(),
  createdat timestamp NOT NULL DEFAULT now(),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE secrets_content (
  id int NOT NULL AUTO_INCREMENT,
  secretid int REFERENCES secrets(id) ON DELETE CASCADE,
  version varchar(20),
  updatedat timestamp NOT NULL DEFAULT now(),
  createdat timestamp NOT NULL DEFAULT now(),
  createdby varchar(255),
  updatedby varchar(255),
  encrypted_content text NOT NULL,
  metadata text NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (secretid, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE users (
  username varchar(255) NOT NULL,
  password_hash varchar(128) NOT NULL,
  updated_at timestamp NOT NULL DEFAULT now(),
  created_at timestamp NOT NULL DEFAULT now(),
  PRIMARY KEY (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
