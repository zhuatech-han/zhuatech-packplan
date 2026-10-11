-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
-- 整数毫米/克；仅建结构，不伪造业务数据。
create table pack_plan (
 id bigint auto_increment primary key, code varchar(30) not null unique, title varchar(120) not null,
 department_id bigint not null, created_by bigint not null, reviewer_id bigint not null,
 category varchar(60) not null, state varchar(20) not null, version bigint not null,
 length int not null, width int not null, height int not null, max_weight_grams bigint not null,
 note varchar(800) not null, active_revision_id bigint null, created_at timestamp(6) not null, updated_at timestamp(6) not null,
 foreign key(department_id) references department(id), foreign key(created_by) references account(id), foreign key(reviewer_id) references account(id),
 check(length>0 and length<=20000 and width>0 and width<=20000 and height>0 and height<=20000),check(max_weight_grams>0 and max_weight_grams<=100000000),check(version>0),check(state in ('DRAFT','PENDING','APPROVED','CANCELLED'))
);
create index ix_plan_scope on pack_plan(department_id,state);
create table cargo_line (
 id bigint auto_increment primary key, plan_id bigint not null, code varchar(30) not null, name varchar(120) not null,
 length int not null,width int not null,height int not null,quantity int not null,weight_grams bigint not null,rotate boolean not null,top_load_grams bigint not null,
 foreign key(plan_id) references pack_plan(id),unique(plan_id,code),
 check(length>0 and length<=20000 and width>0 and width<=20000 and height>0 and height<=20000),check(quantity>0 and quantity<=120),check(weight_grams>0 and weight_grams<=100000000),check(top_load_grams>=0 and top_load_grams<=100000000)
);
create table plan_revision (
 id bigint auto_increment primary key,plan_id bigint not null,created_by bigint not null,kind varchar(20) not null,
 input_json longtext not null,metadata_json longtext not null,layout_json longtext not null,digest varchar(64) not null,created_at timestamp(6) not null,
 foreign key(plan_id) references pack_plan(id),foreign key(created_by) references account(id)
);
alter table pack_plan add constraint fk_plan_active foreign key(active_revision_id) references plan_revision(id);
create index ix_revision_plan on plan_revision(plan_id,id);
create table plan_editor (id bigint auto_increment primary key,plan_id bigint not null,account_id bigint not null,foreign key(plan_id) references pack_plan(id),foreign key(account_id) references account(id),unique(plan_id,account_id));
create table plan_event (id bigint auto_increment primary key,plan_id bigint not null,revision_id bigint null,actor_id bigint not null,action varchar(30) not null,note varchar(800) not null,created_at timestamp(6) not null,foreign key(plan_id) references pack_plan(id),foreign key(revision_id) references plan_revision(id),foreign key(actor_id) references account(id));
create index ix_event_plan on plan_event(plan_id,id);
