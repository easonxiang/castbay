-- GitHub's download counters for the releases' files, read once a day by the Worker's cron
-- (the counters only ever grow, so the difference between two days is that day's downloads).
-- Public numbers: nothing about who downloaded. Rows older than two years are deleted daily.
CREATE TABLE release_downloads (
  day TEXT NOT NULL,         -- YYYY-MM-DD (UTC) of the reading
  tag TEXT NOT NULL,         -- v1.10.0, desktop-v0.1.8
  apk INTEGER NOT NULL,      -- downloads of the release's .apk files
  installers INTEGER NOT NULL, -- downloads of the Desktop installers (.msi, .zip, .deb)
  PRIMARY KEY (day, tag)
);
