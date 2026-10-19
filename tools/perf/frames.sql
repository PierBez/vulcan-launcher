-- The launcher's main-thread frames: how many, how many over 16 ms (60 Hz) and 8.3 ms (120 Hz), and the worst.
with f as (
  select s.dur from slice s join thread_track tt on s.track_id = tt.id join thread t using(utid) join process p using(upid)
  where p.name = 'dev.vulcanlauncher' and t.tid = p.pid and s.name like 'Choreographer#doFrame%' and s.depth = 0)
select count(*) frames, sum(dur > 16000000) over16, sum(dur > 8300000) over8, round(max(dur) / 1e6, 1) max_ms from f;
