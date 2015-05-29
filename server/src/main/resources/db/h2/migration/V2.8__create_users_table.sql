CREATE TABLE users (
  username varchar(255) primary key,
  password_hash varchar(128) NOT NULL,
  created_at timestamp NOT NULL,
  updated_at timestamp NOT NULL
);