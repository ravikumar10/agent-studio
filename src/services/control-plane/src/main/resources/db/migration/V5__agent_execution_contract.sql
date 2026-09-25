alter table agents add column interaction_mode varchar(32) not null default 'TASK_AND_CHAT'
  check (interaction_mode in ('TASK','CHAT','TASK_AND_CHAT'));
alter table agents add column topology varchar(32) not null default 'SINGLE_AGENT'
  check (topology in ('SINGLE_AGENT','MULTI_AGENT'));
alter table agents add column trigger_mode varchar(32) not null default 'ON_DEMAND'
  check (trigger_mode in ('ON_DEMAND','SCHEDULED','EVENT_DRIVEN'));

update agents set interaction_mode='TASK_AND_CHAT' where id in ('website-reader','database-reader');
