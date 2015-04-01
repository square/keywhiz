--
-- PostgreSQL database dump
--


--
-- Data for Name: groups; Type: TABLE DATA; Schema: public; Owner: square
--
INSERT INTO groups (id, name, createdat, updatedat) VALUES
(916,	'Blackops'	,'2012-06-21 14:38:09.888202','2012-06-21 14:38:09.888202'),
(917,'Security','2012-06-21 14:38:09.89200','2012-06-21 14:38:09.892007'),
(918, 'Web',	'2012-06-21 14:38:09.893112','2012-06-21 14:38:09.893112'),
(919, 'iOS',	'2012-06-21 14:38:09.895656','2012-06-21 14:38:09.895656'),
(920, 'DeprecatedGroup', '2013-03-12 11:23:43.123456', '2013-03-12 11:23:43.123456');


--
-- Data for Name: secrets; Type: TABLE DATA; Schema: public; Owner: square
--

INSERT INTO secrets (id, name,  createdat, updatedat) VALUES
(737, 'Nobody_PgPass','2011-09-29 15:46:00.232',	'2011-09-29 15:46:00.232'),
(738,	'Hacking_Password','2011-09-29 15:46:00.312'	,'2011-09-29 15:46:00.312'),
(739,	'Database_Password','2011-09-29 15:46:00.232',	'2011-09-29 15:46:00.232'),
(740,	'General_Password','2011-09-29 15:46:00.312',	'2011-09-29 15:46:00.312'),
(741,	'NonexistentOwner_Pass','2011-09-29 15:46:00.232',	'2011-09-29 15:46:00.232'),
(742,	'Versioned_Password','2011-09-29 15:46:00.232',	'2011-09-29 15:46:00.232'),
(743,	'Multiple_Versioned_Password','2011-09-29 15:46:00.232',	'2011-09-29 15:36:00.232');

INSERT INTO secrets_content (id, secretid, version, createdat, updatedat, encrypted_content, metadata) VALUES
(937, 737, '', '2011-09-29 15:46:00.232', '2015-01-07 12:00:47.674786', '{"derivationInfo":"Nobody_PgPass","content":"5Eq97Y/6LMLUqH8rlXxEkOeMFmc3cYhQny0eotojNrF3DTFdQPyHVG5HeP5vzaFxqttcZkO56NvIwdD8k2xyIL5YRbCIA5MQ9LOnKN4tpnwb+Q","iv":"jQAFJizi1MKZUcCxb6mTCA"}', '{"mode":"0400","owner":"nobody"}'),
(938, 738, '', '2011-09-29 15:46:00.312', '2015-01-07 12:01:59.335018', '{"derivationInfo":"Hacking_Password","content":"jpNVoXZao+b+f591w+CHWTj7D1M","iv":"W+pT37jJP4uDGHmuczXVCA"}', ''),
(939, 739, '', '2011-09-29 15:46:00.232', '2015-01-07 12:02:06.73539', '{"derivationInfo":"Database_Password","content":"etQQFqMHQQpGr4aDlj5gDjiABkOb","iv":"ia+YixjAEqp9W3JEjaYLvQ"}', ''),
(940, 740, '', '2011-09-29 15:46:00.312', '2015-01-07 12:02:06.758446', '{"derivationInfo":"General_Password","content":"A6kBLXwmx0EVtuIGTzxHiEZ/6yrXgg","iv":"e4I0c3fog0TKqTAC2UxYtQ"}', ''),
(941, 741, '', '2011-09-29 15:46:00.232', '2015-01-07 12:02:06.78574', '{"derivationInfo":"NonexistentOwner_Pass","content":"+Pu1B5YgqGRIHzh17s5tPT3AYb+W","iv":"ewRV3RhFfLnbWxY5pr401g"}', '{"owner":"NonExistant","mode":"0400"}'),
(942, 742, '0aae825a73e161d8', '2011-09-29 15:46:00.232', '2015-01-07 12:02:06.806212', '{"derivationInfo":"Versioned_Password","content":"GC8/ZvEfqpxhtAkThgZ8/+vPesh9","iv":"oRf3CMnB7jv63K33dJFeFg"}', ''),
(943, 743, '0aae825a73e161e8', '2011-09-29 16:46:00.232', '2011-09-29 16:46:00.232', '{"derivationInfo":"Multiple_Versioned_Password","content":"GC8/ZvEfqpxhtAkThgZ8/+vPesh9","iv":"oRf3CMnB7jv63K33dJFeFg"}', ''),
(944, 743, '0aae825a73e161f8', '2011-09-29 17:46:00.232', '2011-09-29 17:46:00.232', '{"derivationInfo":"Multiple_Versioned_Password","content":"GC8/ZvEfqpxhtAkThgZ8/+vPesh9","iv":"oRf3CMnB7jv63K33dJFeFg"}', ''),
(945, 743, '0aae825a73e161g8', '2011-09-29 18:46:00.232', '2011-09-29 18:46:00.232', '{"derivationInfo":"Multiple_Versioned_Password","content":"GC8/ZvEfqpxhtAkThgZ8/+vPesh9","iv":"oRf3CMnB7jv63K33dJFeFg"}', '');

--
-- Data for Name: clients; Type: TABLE DATA; Schema: public; Owner: square
--

INSERT INTO clients (id, name, createdat, updatedat, enabled, automationAllowed) VALUES
(768,	'client',	'2012-06-21 14:38:09.867533',	'2012-06-21 14:38:09.867533', true, true),
(769,	'CN=User1',	'2012-06-21 14:38:09.872075',	'2012-06-21 14:38:09.872075', true, false),
(770,	'CN=User2',	'2012-06-21 14:38:09.87328',	'2012-06-21 14:38:09.87328', true, false),
(771,	'CN=User3',	'2012-06-21 14:38:09.874214',	'2012-06-21 14:38:09.874214', true, false),
(772,	'CN=User4',	'2012-06-21 14:38:09.875291',	'2012-06-21 14:38:09.875291', true, false);


--
-- Data for Name: accessgrants; Type: TABLE DATA; Schema: public; Owner: square
--

INSERT INTO accessgrants (id, groupid, secretid, createdat, updatedat) VALUES
(617,	918,	737,	'2012-06-21 14:38:09.984113',	'2012-06-21 14:38:09.984113'),
(618,	917,  737,	'2012-06-21 14:38:09.990935',	'2012-06-21 14:38:09.990935'),
(619,	916,	738,	'2012-06-21 14:38:09.992612',	'2012-06-21 14:38:09.992612'),
(620,	918,	739,	'2012-06-21 14:38:09.995025',	'2012-06-21 14:38:09.995025'),
(621,	917,	739,	'2012-06-21 14:38:09.996522',	'2012-06-21 14:38:09.996522'),
(622,	918,	740,	'2012-06-21 14:38:09.998356',	'2012-06-21 14:38:09.998356'),
(623,	919,	740,	'2012-06-21 14:38:10.000046',	'2012-06-21 14:38:10.000046'),
(624,	916,	740,	'2012-06-21 14:38:10.00146',	'2012-06-21 14:38:10.00146'),
(625,	917,	740,	'2012-06-21 14:38:10.002938',	'2012-06-21 14:38:10.002938'),
(626,	918,	741,	'2012-06-21 14:38:11.984113',	'2012-06-21 14:38:11.984113'),
(627,	917,  741,	'2012-06-21 14:38:11.990935',	'2012-06-21 14:38:11.990935');

--
-- Data for Name: memberships; Type: TABLE DATA; Schema: public; Owner: square
--

INSERT INTO memberships (id, groupid, clientid, createdat, updatedat) VALUES
(659,	917,	768,	'2012-06-21 14:38:09.957063',	'2012-06-21 14:38:09.957063'),
(660,	918,	769,	'2012-06-21 14:38:09.970642',	'2012-06-21 14:38:09.970642'),
(661,	916,	769,	'2012-06-21 14:38:09.972122',	'2012-06-21 14:38:09.972122'),
(662,	917,	769,	'2012-06-21 14:38:09.974132',	'2012-06-21 14:38:09.974132'),
(663,	919,	770,	'2012-06-21 14:38:09.975571',	'2012-06-21 14:38:09.975571'),
(664,	917,	770,	'2012-06-21 14:38:09.976875',	'2012-06-21 14:38:09.976875'),
(665,	918,	771,	'2012-06-21 14:38:09.978106',	'2012-06-21 14:38:09.978106'),
(666,	919,	771,	'2012-06-21 14:38:09.979935',	'2012-06-21 14:38:09.979935'),
(667,	918,	772,	'2012-06-21 14:38:09.981239',	'2012-06-21 14:38:09.981239'),
(668,	917,	772,	'2012-06-21 14:38:09.982586', '2012-06-21 14:38:09.982586');

--
-- Data for BCrypt authentication.  User: keywhizAdmin Pass: BCrypt.hashpw(adminPass)
--

INSERT INTO users (username, password_hash, created_at, updated_at) VALUES
('keywhizAdmin',	'$2a$10$zan1PdXNZmBK2/s89.AvKunl5Mv8pGq5xoTipxUrVjY3Rezb.XJ8C', '2012-06-22 14:38:09.982586', '2012-06-22 14:38:09.982586');


-- Set the sequences appropriately.
ALTER SEQUENCE clients_id_seq RESTART WITH 1000;
ALTER SEQUENCE groups_id_seq RESTART WITH 1000;
ALTER SEQUENCE secrets_id_seq RESTART WITH 1000;
ALTER SEQUENCE accessgrants_id_seq RESTART WITH 1000;
ALTER SEQUENCE memberships_id_seq RESTART WITH 1000;


--
-- PostgreSQL database dump complete
--

