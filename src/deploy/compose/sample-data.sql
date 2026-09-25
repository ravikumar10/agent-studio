create table products(id bigint primary key,name text not null,category text not null,price numeric(10,2) not null,stock integer not null);
insert into products values
 (1,'Trail Backpack','outdoors',89.00,14),
 (2,'Insulated Bottle','outdoors',24.50,42),
 (3,'Mechanical Keyboard','electronics',119.00,8),
 (4,'USB-C Dock','electronics',79.00,21),
 (5,'Desk Lamp','home',39.95,17);
grant select on products to sample_reader;
