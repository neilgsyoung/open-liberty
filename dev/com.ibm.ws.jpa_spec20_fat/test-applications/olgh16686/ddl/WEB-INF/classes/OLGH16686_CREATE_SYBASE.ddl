CREATE TABLE ${schemaname}.ELEMENTCOLLECTIONENTITYOLGH166 (id INT NOT NULL, UNQ_INDEX NUMERIC IDENTITY UNIQUE, PRIMARY KEY (id));
CREATE TABLE ${schemaname}.EntMapDateTemporal (ELEME_ID INT NULL, mykey DATETIME NOT NULL, temporalValue DATETIME NULL, UNQ_INDEX NUMERIC IDENTITY UNIQUE);
CREATE INDEX ${schemaname}.I_NTMPPRL_ELEME_ID ON ${schemaname}.EntMapDateTemporal (ELEME_ID);