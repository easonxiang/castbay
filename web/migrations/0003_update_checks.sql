-- How many times the apps asked for latest.json (the once-a-day update check), by day, app
-- version and path (web: castbay.weenas.com; relay: cast.weenas.com). Only a count: no address,
-- no device identifier, and it covers every app, whether or not its owner turned statistics on.
-- app is a release version, or 'other' for anything else (a browser, a test build, a crawler).
-- Rows older than a year are deleted daily.
CREATE TABLE update_checks (
  day TEXT NOT NULL,         -- YYYY-MM-DD (UTC)
  app TEXT NOT NULL,         -- 1.10.0, 1.10.0-beta.1, or other
  via TEXT NOT NULL,         -- web / relay
  hits INTEGER NOT NULL,
  PRIMARY KEY (day, app, via)
);
