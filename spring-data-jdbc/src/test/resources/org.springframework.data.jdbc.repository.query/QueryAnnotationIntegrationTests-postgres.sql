DROP TABLE dummy_entity;
DROP TABLE enum_entity;
CREATE TABLE dummy_entity ( id SERIAL PRIMARY KEY, NAME VARCHAR(100));
CREATE TABLE enum_entity ( id SERIAL PRIMARY KEY, dummy_enum VARCHAR(100));
