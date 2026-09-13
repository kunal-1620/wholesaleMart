alter table stored_files add column if not exists size_bytes bigint not null default 0;

update stored_files
set size_bytes = coalesce(octet_length(data), 0)
where data is not null
  and size_bytes = 0;
