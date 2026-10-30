-- System roles. Permissions are editable by administrators; the role codes are stable.
insert into app_role (code, name, system_role) values
    ('ADMIN',    'Administrator', true),
    ('MANAGER',  'Manager',       true),
    ('EXECUTOR', 'Executor',      true),
    ('VIEWER',   'Viewer',        true);

insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
cross join (values
    ('MEETING_READ'), ('MEETING_WRITE'),
    ('AGREEMENT_READ'), ('AGREEMENT_WRITE'), ('AGREEMENT_APPROVE'),
    ('ITEM_UPDATE'), ('REPORT_EXPORT'),
    ('USER_ADMIN'), ('REFERENCE_ADMIN'), ('AUDIT_READ')
) as p (permission)
where r.code = 'ADMIN';

insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
cross join (values
    ('MEETING_READ'), ('MEETING_WRITE'),
    ('AGREEMENT_READ'), ('AGREEMENT_WRITE'),
    ('ITEM_UPDATE'), ('REPORT_EXPORT')
) as p (permission)
where r.code = 'MANAGER';

insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
cross join (values ('MEETING_READ'), ('AGREEMENT_READ'), ('ITEM_UPDATE')) as p (permission)
where r.code = 'EXECUTOR';

insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
cross join (values ('MEETING_READ'), ('AGREEMENT_READ'), ('REPORT_EXPORT')) as p (permission)
where r.code = 'VIEWER';

insert into reference_item (kind, code, name_uz, name_ru, name_en) values
    ('AGREEMENT_TYPE', 'MOU',        'Oʻzaro anglashuv memorandumi', 'Меморандум о взаимопонимании', 'Memorandum of understanding'),
    ('AGREEMENT_TYPE', 'AGREEMENT',  'Bitim',                        'Соглашение',                   'Agreement'),
    ('AGREEMENT_TYPE', 'PROGRAMME',  'Hamkorlik dasturi',            'Программа сотрудничества',     'Cooperation programme'),
    ('AGREEMENT_TYPE', 'ROADMAP',    'Yoʻl xaritasi',                'Дорожная карта',               'Roadmap'),
    ('MEETING_FORMAT', 'BILATERAL',  'Ikki tomonlama',               'Двусторонняя',                 'Bilateral'),
    ('MEETING_FORMAT', 'MULTILATERAL','Koʻp tomonlama',              'Многосторонняя',               'Multilateral'),
    ('MEETING_FORMAT', 'ONLINE',     'Onlayn',                       'Онлайн',                       'Online'),
    ('COUNTRY', 'DE', 'Germaniya',     'Германия',           'Germany'),
    ('COUNTRY', 'JP', 'Yaponiya',      'Япония',             'Japan'),
    ('COUNTRY', 'KR', 'Koreya Respublikasi', 'Республика Корея', 'Republic of Korea'),
    ('COUNTRY', 'TR', 'Turkiya',       'Турция',             'Türkiye'),
    ('COUNTRY', 'FR', 'Fransiya',      'Франция',            'France'),
    ('COUNTRY', 'KZ', 'Qozogʻiston',   'Казахстан',          'Kazakhstan');
