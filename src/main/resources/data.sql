insert into folk (id, surname, name, patronymic, preferred_name, gender, birth_date, birth_place, company, position, city, speaker, organizator, vip_flag, visit_count, average_rating, language)
values (1, 'Иванов', 'Пётр', 'Сергеевич', 'Петя', 1, DATE '1984-03-12', 'Москва', 'ОАО Ромашка', 'Java Developer', 'Москва', 1, 0, 2, 4, 4.80, 'ru');

insert into folk (id, surname, name, patronymic, preferred_name, gender, birth_date, birth_place, company, position, city, speaker, organizator, vip_flag, visit_count, average_rating, language)
values (2, 'Иванова', 'Мария', 'Андреевна', 'Маша', 2, DATE '1990-07-02', 'Казань', 'HFLabs', 'QA Lead', 'Казань', 1, 1, 2, 6, 4.95, 'ru');

insert into folk (id, surname, name, patronymic, preferred_name, gender, birth_date, birth_place, company, position, city, speaker, organizator, vip_flag, visit_count, average_rating, language)
values (3, 'Сидоров', 'Илья', 'Олегович', null, 1, DATE '1979-11-30', 'Новосибирск', 'ИП Иванов', 'Консультант', 'Новосибирск', 0, 0, 0, 1, 3.40, 'ru');

insert into folk (id, surname, name, patronymic, preferred_name, gender, birth_date, birth_place, company, position, city, speaker, organizator, vip_flag, visit_count, average_rating, language)
values (4, 'Смирнова', 'Ольга', 'Игоревна', 'Оля', 2, DATE '1995-01-19', 'Санкт-Петербург', 'ОАО Ромашка', 'Аналитик', 'Санкт-Петербург', 0, 1, 1, 3, 4.10, 'ru');

insert into device (id, type, os, model, serial_number, author, folk)
values (1, 'IPAD', 'IOS', 'iPad', 'X234F', 'seed', 1);

insert into device (id, type, os, model, serial_number, author, folk)
values (2, 'SAMSUNG', 'ANDROID', 'Pixel', 'A-19', 'seed', 2);
