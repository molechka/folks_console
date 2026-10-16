# Folks

Учебный сервис участников конференции (SQA). Один JAR: SOAP, REST и страница UI на встроенном Jetty. Конфигурация — Spring XML, данные — HSQLDB в памяти, поиск — Lucene.

Отвечай пользователю по-русски. Комментарии и Javadoc в коде — на русском, в том же стиле, что уже есть в файле.

## Запуск и проверка

Рабочий каталог — корень репозитория. Сборка и запуск только через Maven Wrapper.

```powershell
.\mvnw.cmd -q test
.\mvnw.cmd -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.mainClass=ru.olgak.folks.app.FolksApplication" "-Dexec.classpathScope=runtime"
```

Точка входа — `ru.olgak.folks.app.FolksApplication`. Порт **8088**:

- UI: http://localhost:8088/ui/
- REST: http://localhost:8088/rest/services/folks/v1
- SOAP: http://localhost:8088/soap/services/folks/v1?wsdl

База живёт в памяти процесса. После перезапуска остаются только строки из `src/main/resources/data.sql`.

Один прогон поиска: `.\mvnw.cmd -q -Dtest=SearchCase test`. Аналогично `RestCase`, `SoapCase`, `ComplexSearchCase`. Surefire подхватывает классы `*Test*` и `*Case*`.

## Стек и ограничения

- Хост — JDK 25, байткод — **Java 11** (`maven-compiler-plugin` / `<release>11</release>`). Spring 5.2 не читает class file 69. Новый код должен компилироваться в 11: без записей, `switch`-выражений, текстовых блоков и прочих конструкций новее 11.
- Родительский POM и артефакты `ru.hflabs` берутся из локального репозитория `lib/`. Версии зависимостей задаёт parent `19.8-SNAPSHOT`, утилиты — `20.15-SNAPSHOT`.
- Checkstyle в POM отключён.
- Контекст собирается из `applicationContext.xml` и импортов `services-database.xml`, `services-search.xml`, `services-soap.xml`, `services-rest.xml`. Новые бины клади в соответствующий `services-*.xml`, а не в Java-конфигурацию.
- Схема БД — `src/main/resources/schema.sql`. Hibernate работает с `hbm2ddl.auto=validate` и `ImprovedNamingStrategy`. Новое поле сущности — это колонка в `schema.sql`, маппинг в сущности и при необходимости строка в `data.sql`. Имена колонок задавай явно через `@Column`, если имя в Java не совпадает со snake_case.

## Пакеты

| Пакет | Роль |
| --- | --- |
| `ru.olgak.folks.api` | JPA-сущности `Folk`, `Device`, `AbstractEntity` |
| `ru.olgak.folks.api.model` | Перечисления (`Gender`, `DeviceType`, `DeviceOS`, `ThreeStateStatus`) |
| `ru.olgak.folks.api.search` | `EntityQuery`, `EntityFieldFilter`, интерфейс `SearchService` |
| `ru.olgak.folks.api.annotation` | `@Searchable` |
| `ru.olgak.folks.service` | `FolkStore` — создание, изменение, чтение, удаление и синхронизация индекса |
| `ru.olgak.folks.service.dao` | Spring Data `FolkRepository` |
| `ru.olgak.folks.service.search` | Lucene: `FolkLuceneSearchService`, `SearchBinderFactory`, `LuceneQueryParser` |
| `ru.olgak.folks.soap` | Контракт SOAP (сгенерирован CXF) и реализация `FolksWSImpl` |
| `ru.olgak.folks.app` | Запуск Jetty, демо-данные, раздача UI |

`FolksWS` и соседние JAXB-типы с `@Generated` — зафиксированный контракт из `src/main/resources/wsdl/folks.wsdl`. Плагин `cxf-codegen-plugin` в `pom.xml` закомментирован. Правь реализацию в `FolksWSImpl` и `FolkStore`. WSDL и сгенерированные типы меняй только если меняется контракт, и тогда же раскомментируй генерацию.

REST и SOAP обслуживает один бин `folksWS`. JSON-методы (`GET /{id}`, `DELETE /{id}`) живут только на `FolksWSImpl` и в интерфейс `FolksWS` не входят. Пример тела запроса — `examples_rest.txt`. Даты в API — `dd.MM.yyyy`.

## Поиск

Полнотекст и фильтры строятся рефлексией по `@Searchable` на поле или геттере (`SearchBinderFactory`):

- `search = true` — значение попадает в общий полнотекстовый запрос `EntityQuery.search`;
- `filter = true` — поле доступно в `EntityQuery.filters` и расширенном поиске;
- `sort = true` — поле можно указать в `sortOrderKey`.

Поле без нужного флага в соответствующий режим не попадает. Вложенные `Device` индексируются вместе с `Folk`. После изменения данных индекс обновляет `FolkStore`; тесты перед запросом вызывают `searchService.rebuild()`.

## Тесты

Базовый класс — `FeatureCase` (TestNG + Spring + DBUnit, HSQLDB). Кейс — каталог под `src/test/resources/cases/`:

| Каталог | Класс | Что проверяет |
| --- | --- | --- |
| `cases/search/` | `SearchCase` | полнотекст и сортировка, ожидаемые id |
| `cases/complex_search/` | `ComplexSearchCase` | фильтры, сортировка, расширенный запрос |
| `cases/rest/` | `RestCase` | HTTP на живой endpoint, `request.json` / `response.json` |
| `cases/soap/` | `SoapCase` | SOAP, `request.xml` / `response.xml`, `transform.xslt` |

Кейс поиска:

- `dbStart.xls` — данные DBUnit на старт;
- `case-params.xml` — бин `toSearchDescriptor.<имя_папки>`, наследник абстрактного `toSearchDescriptor` из `cases/search/case-params.xml`;
- `expectedResult` — список id `Folk`. Пустой список — пустая выборка. При сортировке важен и порядок id.

Имя папки совпадает с суффиксом id бина (`test_s_01_search_by_surname`). Префиксы уже занятых групп: `test_s_` простой поиск, `test_se_` расширенный, `test_so_` сортировка. Пропуск кейса — строка в `cases/search/skipTests.properties`.

`FeatureCase` подхватывает `services-*.xml` и `**/case-*.xml` с classpath. Общий дескриптор группы клади в `case-params.xml` корня группы, параметры конкретного кейса — в его каталог.

## Что сохранять

- Существующие сигнатуры `SearchService`, `FolkStore` и публичный контракт `FolksWS`.
- Идентификаторы Spring-бинов: `searchService`, `folkRepository`, `folkStore`, `folksWS`, `dataSource`.
- Демо-наполнение `data.sql`: четыре участника и их устройства — на них завязан ручной сценарий UI.
- Локальный `lib/` и закомментированный блок генерации CXF. Не подключай внешний parent и не заменяй XML-контекст на Java `@Configuration`.
