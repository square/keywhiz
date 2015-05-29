CREATE TABLE secrets (
  id SERIAL primary key,
  name varchar(255),
  secret text,
  version varchar(20),
  owner text,
  mode text,
  createdAt timestamp,
  updatedAt timestamp
);

CREATE TABLE groups (
  id SERIAL primary key,
  name varchar(255) unique,
  createdAt timestamp,
  updatedAt timestamp
);

CREATE TABLE clients (
  id SERIAL primary key,
  name varchar(255) unique,
  createdAt timestamp,
  updatedAt timestamp
);

CREATE TABLE accessGrants (
  id SERIAL primary key,
  groupId int REFERENCES groups ON DELETE CASCADE,
  secretId int REFERENCES secrets ON DELETE CASCADE,
  createdAt timestamp,
  updatedAt timestamp
);

CREATE TABLE memberships (
  id SERIAL primary key,
  groupId int REFERENCES groups ON DELETE CASCADE,
  clientId int REFERENCES clients ON DELETE CASCADE,
  createdAt timestamp,
  updatedAt timestamp
);
