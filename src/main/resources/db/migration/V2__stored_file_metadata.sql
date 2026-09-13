alter table stored_files add column if not exists storage_provider varchar(255) not null default 'database';
alter table stored_files add column if not exists object_key varchar(1000);
alter table stored_files add column if not exists public_url varchar(1000);
alter table stored_files alter column data drop not null;
create index if not exists idx_stored_files_provider_key on stored_files (storage_provider, object_key);
