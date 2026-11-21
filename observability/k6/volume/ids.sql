-- k6 데이터량 스크립트가 쓰는 사용자별 번호 — 결과를 volume/ids.json 으로 저장한다(생성 파일, 커밋하지 않는다)
--   docker compose exec -T loadtest-db psql -U drive_load -d drive_load -At < k6/volume/ids.sql > k6/volume/ids.json
-- seq: 사용자 번호, folder: seed 폴더, big: big-folder, deep: deep-30, file: 루트 파일, volFolders: 루트 폴더 500개
select json_object_agg(u.user_id, json_build_object(
  'seq',    u.user_seq,
  'folder', (select min(resource_seq) from tb_resource r where r.user_seq=u.user_seq and resource_type='FOLDER' and parent_folder_seq is null and resource_name like '%-folder-%' and resource_name not like 'vol-%'),
  'big',    (select min(resource_seq) from tb_resource r where r.user_seq=u.user_seq and resource_name='big-folder' and is_delete='N'),
  'deep',   (select min(resource_seq) from tb_resource r where r.user_seq=u.user_seq and resource_name='deep-30'),
  'file',   (select min(resource_seq) from tb_resource r where r.user_seq=u.user_seq and resource_type='FILE' and parent_folder_seq is null),
  'volFolders', (select json_agg(resource_seq order by resource_seq) from tb_resource r where r.user_seq=u.user_seq and parent_folder_seq is null and resource_name like 'vol-folder-%' and is_delete='N')))
from tb_user u where u.user_id like 'lt-user-%';
