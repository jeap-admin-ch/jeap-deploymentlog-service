alter table environment
    add column development boolean;

update environment e
set development = false;

update environment e
set development = true
where upper(e.name) = 'DEV';