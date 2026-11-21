-- 데이터량 테스트용 대량 데이터 — 부하 테스트 DB(drive_load) 전용. seed.js 를 먼저 실행한 뒤 한 번만 실행한다(약 110만 건, 수 분).
--   docker compose exec -T loadtest-db psql -U drive_load -d drive_load < k6/volume/seed-volume.sql
-- 사용자(lt-user-*)마다: 루트 폴더 500개 × 파일 100개, big-folder(파일 10,000개), deep-01 ~ deep-30(깊이 30, 단계마다 파일 10개)
-- 행만 만든다(스토리지 객체 없음) — 목록·검색·트리·삭제처럼 DB 만 보는 API 용. 이 파일들은 내려받을 수 없다.
-- 저장 키(resource_save_name)는 사용자·폴더마다 달라야 한다 — 같으면 삭제 대기열이 "아직 참조 중"으로 건너뛴다.
do $$
declare u record; f bigint; parent bigint; ppath text; pname text; kw text[] := array['report','budget','plan','design','meeting','contract','memo','draft','invoice','roadmap'];
begin
for u in select user_seq from tb_user where user_id like 'lt-user-%' order by user_seq loop
  insert into tb_resource(user_seq, owner_seq, latest_owner_seq, resource_type, resource_name, register_user_seq, update_user_seq, update_date)
  select u.user_seq, u.user_seq, u.user_seq, 'FOLDER', 'vol-folder-'||lpad(g::text,4,'0'), u.user_seq, u.user_seq, now() - (g||' minutes')::interval
  from generate_series(1,500) g;
  update tb_resource set full_path_seq = resource_seq||'/', full_path = resource_name||'/'
   where user_seq=u.user_seq and resource_name like 'vol-folder-%' and full_path_seq is null;
  insert into tb_resource(user_seq, owner_seq, latest_owner_seq, resource_type, parent_folder_seq, resource_name, original_file_name, resource_save_name, ext, content_type, size_byte, register_user_seq, update_user_seq, update_date)
  select u.user_seq, u.user_seq, u.user_seq, 'FILE', r.resource_seq, kw[1+(g%10)]||'-vol-'||r.resource_seq||'-'||g||'.txt', kw[1+(g%10)]||'.txt', 'vol/'||r.resource_seq||'/'||g, 'txt', 'text/plain', 1000, u.user_seq, u.user_seq, now() - ((r.resource_seq%5000 + g)||' seconds')::interval
  from tb_resource r cross join generate_series(1,100) g
  where r.user_seq=u.user_seq and r.resource_name like 'vol-folder-%';
  -- big-folder
  insert into tb_resource(user_seq, owner_seq, latest_owner_seq, resource_type, resource_name, register_user_seq, update_user_seq, update_date)
  values (u.user_seq, u.user_seq, u.user_seq, 'FOLDER', 'big-folder', u.user_seq, u.user_seq, now()) returning resource_seq into f;
  update tb_resource set full_path_seq = f||'/', full_path = 'big-folder/' where user_seq=u.user_seq and resource_seq=f;
  insert into tb_resource(user_seq, owner_seq, latest_owner_seq, resource_type, parent_folder_seq, resource_name, original_file_name, resource_save_name, ext, content_type, size_byte, register_user_seq, update_user_seq, update_date)
  select u.user_seq, u.user_seq, u.user_seq, 'FILE', f, kw[1+(g%10)]||'-big-'||g||'.txt', kw[1+(g%10)]||'.txt', 'vol/big/'||f||'/'||g, 'txt', 'text/plain', 1000, u.user_seq, u.user_seq, now() - (g||' seconds')::interval
  from generate_series(1,10000) g;
  -- deep chain
  parent := null; ppath := ''; pname := '';
  for d in 1..30 loop
    insert into tb_resource(user_seq, owner_seq, latest_owner_seq, resource_type, parent_folder_seq, resource_name, register_user_seq, update_user_seq, update_date)
    values (u.user_seq, u.user_seq, u.user_seq, 'FOLDER', parent, 'deep-'||lpad(d::text,2,'0'), u.user_seq, u.user_seq, now()) returning resource_seq into f;
    ppath := ppath||f||'/'; pname := pname||'deep-'||lpad(d::text,2,'0')||'/';
    update tb_resource set full_path_seq = ppath, full_path = pname where user_seq=u.user_seq and resource_seq=f;
    insert into tb_resource(user_seq, owner_seq, latest_owner_seq, resource_type, parent_folder_seq, resource_name, original_file_name, resource_save_name, ext, content_type, size_byte, register_user_seq, update_user_seq, update_date)
    select u.user_seq, u.user_seq, u.user_seq, 'FILE', f, kw[1+(g%10)]||'-deep-'||d||'-'||g||'.txt', 'x.txt', 'vol/deep/'||f||'/'||g, 'txt', 'text/plain', 1000, u.user_seq, u.user_seq, now()
    from generate_series(1,10) g;
    parent := f;
  end loop;
  update tb_user set use_quota_byte = use_quota_byte + (50000+10000+300)*1000 where user_seq=u.user_seq;
end loop;
end $$;
analyze;
select count(*) total, count(*) filter (where resource_type='FOLDER') folders from tb_resource;
