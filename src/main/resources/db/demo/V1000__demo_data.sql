-- Demo data: loaded only with the "demo" or "test" profile (see application.yml).
-- All people, organizations and agreements here are fictional.
-- Every demo account uses the password  Demo2026!pass

insert into reference_item (kind, code, name_uz, name_ru, name_en) values
    ('ORGANIZATION', 'TRADE_AGENCY',  'Savdo agentligi (namuna)',      'Торговое агентство (пример)',      'Trade Agency (sample)'),
    ('ORGANIZATION', 'DIGITAL_MIN',   'Raqamli rivojlanish vazirligi (namuna)', 'Министерство цифрового развития (пример)', 'Ministry of Digital Development (sample)'),
    ('ORGANIZATION', 'EDU_COUNCIL',   'Taʼlim kengashi (namuna)',      'Совет по образованию (пример)',    'Education Council (sample)');

insert into app_user (username, full_name, email, department, password_hash, oneid_subject, preferred_lang) values
    ('admin',     'Aziz Rahimov',      'admin@example.org',     'IT',                    '$2a$10$gZliM49pdaM.veW1c3Wz.OJcKhsMyVfIffkuCfXEF.Rzwy1wFLL1C', 'demo-0001', 'uz'),
    ('manager',   'Dilnoza Karimova',  'manager@example.org',   'International Relations','$2a$10$gZliM49pdaM.veW1c3Wz.OJcKhsMyVfIffkuCfXEF.Rzwy1wFLL1C', 'demo-0002', 'uz'),
    ('executor1', 'Bekzod Tursunov',   'executor1@example.org', 'Trade Department',      '$2a$10$gZliM49pdaM.veW1c3Wz.OJcKhsMyVfIffkuCfXEF.Rzwy1wFLL1C', 'demo-0003', 'ru'),
    ('executor2', 'Malika Yusupova',   'executor2@example.org', 'Education Department',  '$2a$10$gZliM49pdaM.veW1c3Wz.OJcKhsMyVfIffkuCfXEF.Rzwy1wFLL1C', 'demo-0004', 'en'),
    ('viewer',    'Sardor Aliyev',     'viewer@example.org',    'Analytics',             '$2a$10$gZliM49pdaM.veW1c3Wz.OJcKhsMyVfIffkuCfXEF.Rzwy1wFLL1C', null,        'uz');

insert into user_role (user_id, role_id)
select u.id, r.id from app_user u join app_role r on
    (u.username = 'admin' and r.code = 'ADMIN') or
    (u.username = 'manager' and r.code = 'MANAGER') or
    (u.username in ('executor1', 'executor2') and r.code = 'EXECUTOR') or
    (u.username = 'viewer' and r.code = 'VIEWER');

insert into meeting (title, format_id, country_id, organization_id, held_on, location, status, summary, responsible_id, created_by)
values
    ('Intergovernmental commission on trade, 5th session',
     (select id from reference_item where kind = 'MEETING_FORMAT' and code = 'BILATERAL'),
     (select id from reference_item where kind = 'COUNTRY' and code = 'KR'),
     (select id from reference_item where kind = 'ORGANIZATION' and code = 'TRADE_AGENCY'),
     current_date - 40, 'Tashkent', 'HELD', 'Agreed on a roadmap for trade facilitation.',
     (select id from app_user where username = 'manager'), 'admin'),
    ('Working group on e-government services',
     (select id from reference_item where kind = 'MEETING_FORMAT' and code = 'ONLINE'),
     (select id from reference_item where kind = 'COUNTRY' and code = 'DE'),
     (select id from reference_item where kind = 'ORGANIZATION' and code = 'DIGITAL_MIN'),
     current_date + 14, 'Online', 'PLANNED', null,
     (select id from app_user where username = 'manager'), 'admin');

insert into agreement (registration_number, title, type_id, country_id, organization_id, meeting_id,
                       signed_on, valid_until, status, description, responsible_id, created_by)
values
    ('INT-2026-001', 'Roadmap on trade facilitation',
     (select id from reference_item where kind = 'AGREEMENT_TYPE' and code = 'ROADMAP'),
     (select id from reference_item where kind = 'COUNTRY' and code = 'KR'),
     (select id from reference_item where kind = 'ORGANIZATION' and code = 'TRADE_AGENCY'),
     (select id from meeting where title like 'Intergovernmental commission%'),
     current_date - 38, current_date + 60, 'IN_FORCE', 'Customs simplification and joint trade fairs.',
     (select id from app_user where username = 'manager'), 'admin'),
    ('INT-2026-002', 'Memorandum on digital government cooperation',
     (select id from reference_item where kind = 'AGREEMENT_TYPE' and code = 'MOU'),
     (select id from reference_item where kind = 'COUNTRY' and code = 'DE'),
     (select id from reference_item where kind = 'ORGANIZATION' and code = 'DIGITAL_MIN'),
     null, null, null, 'UNDER_REVIEW', 'Exchange of experience on e-services.',
     (select id from app_user where username = 'manager'), 'admin'),
    ('INT-2026-003', 'Programme of cooperation in higher education',
     (select id from reference_item where kind = 'AGREEMENT_TYPE' and code = 'PROGRAMME'),
     (select id from reference_item where kind = 'COUNTRY' and code = 'JP'),
     (select id from reference_item where kind = 'ORGANIZATION' and code = 'EDU_COUNCIL'),
     null, current_date - 400, current_date + 700, 'IN_FORCE', 'Student exchange and joint programmes.',
     (select id from app_user where username = 'manager'), 'admin'),
    ('INT-2026-004', 'Agreement on investment promotion',
     (select id from reference_item where kind = 'AGREEMENT_TYPE' and code = 'AGREEMENT'),
     (select id from reference_item where kind = 'COUNTRY' and code = 'TR'),
     null, null, null, null, 'DRAFT', null,
     (select id from app_user where username = 'manager'), 'admin');

insert into agreement_status_history (agreement_id, from_status, to_status, comment, changed_by)
select id, null, status, 'Imported', 'admin' from agreement;

insert into implementation_item (agreement_id, description, responsible_id, due_date, status, progress_note)
values
    ((select id from agreement where registration_number = 'INT-2026-001'),
     'Prepare a list of goods for simplified customs procedures',
     (select id from app_user where username = 'executor1'), current_date + 3, 'IN_PROGRESS', 'Draft list circulated.'),
    ((select id from agreement where registration_number = 'INT-2026-001'),
     'Organise a joint trade fair',
     (select id from app_user where username = 'executor1'), current_date - 5, 'NOT_STARTED', null),
    ((select id from agreement where registration_number = 'INT-2026-001'),
     'Set up a joint working group',
     (select id from app_user where username = 'executor2'), current_date - 20, 'DONE', 'Group established.'),
    ((select id from agreement where registration_number = 'INT-2026-003'),
     'Launch the student exchange call',
     (select id from app_user where username = 'executor2'), current_date + 25, 'NOT_STARTED', null);

update implementation_item set completed_on = current_date - 22 where status = 'DONE';
