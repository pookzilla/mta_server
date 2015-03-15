DROP SCHEMA mta IF EXISTS CASCADE;
CREATE SCHEMA mta;

CREATE TABLE mta.trips (
route_id VARCHAR(4),
service_id VARCHAR(32),
trip_id VARCHAR(32) PRIMARY KEY,
trip_headsign VARCHAR(128),
direction_id INTEGER,
block_id INTEGER,
shape_id VARCHAR(32));

CREATE INDEX trips_trip_id ON mta.trips (trip_id);
CREATE INDEX trips_route_id ON mta.trips (route_id);

CREATE TABLE mta.routes (
route_id VARCHAR(16) PRIMARY KEY,
agency_id VARCHAR(16),
route_short_name VARCHAR(16),
route_long_name VARCHAR(128),
route_desc LONGVARCHAR,
route_type INTEGER,
route_url LONGVARCHAR,
route_color VARCHAR(32),
route_text_color VARCHAR(32));

CREATE INDEX routes_route_id ON mta.routes (route_id);

CREATE TABLE mta.stop_times (
trip_id VARCHAR(32),
arrival_time INTEGER,
departure_time INTEGER,
stop_id VARCHAR(16),
stop_sequence INTEGER,
stop_headsign VARCHAR(32),
pickup_type INTEGER,
drop_off_type INTEGER,
shape_dist_traveled INTEGER);

CREATE INDEX stop_times_stop_id ON mta.stop_times (stop_id);
CREATE INDEX stop_times_trip_id ON mta.stop_times (trip_id);

CREATE TABLE mta.stops (
stop_id VARCHAR(16) PRIMARY KEY,
stop_code VARCHAR(16),
stop_name VARCHAR(64),
stop_desc LONGVARCHAR,
stop_lat DOUBLE,
stop_lon DOUBLE,
zone_id INTEGER,
stop_url INTEGER,
location_type INTEGER,
parent_station VARCHAR(32));

CREATE INDEX stops_stop_name ON mta.stops (stop_name);

CREATE TABLE mta.calendar (
service_id VARCHAR(16) PRIMARY KEY,
monday BOOLEAN,
tuesday BOOLEAN,
wednesday BOOLEAN,
thursday BOOLEAN,
friday BOOLEAN,
saturday BOOLEAN,
sunday BOOLEAN,
start_date VARCHAR(16),
end_date VARCHAR(16));