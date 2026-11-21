# Shop API

REST API сервиса доставки еды на Java. Учебный проект — порт C#-приложения (ASP.NET Core Minimal API) на Spring Boot.

## Возможности

- Регистрация и вход, JWT-аутентификация, роли `USER` / `ADMINISTRATOR`
- Каталог: категории и блюда (чтение — публично, изменение — только администратор)
- Адреса доставки пользователя
- Заказы со сменой статусов (`CREATED → PAID → COOKING → DELIVERING → COMPLETED / CANCELLED`)
- Уведомления об изменении статуса заказа (push-заглушка и email) после коммита транзакции, асинхронно
- Ограничение частоты запросов на вход и регистрацию
- Единый формат ошибок `ProblemDetail` (RFC 9457)
- Документация API через Swagger UI
- Метрики производительности через Actuator / Micrometer и Prometheus

## Стек

Java 21 · Spring Boot 4 · Spring Web MVC · Spring Security (OAuth2 Resource Server, JWT HS256) · Spring Data JPA / Hibernate · PostgreSQL · Flyway · Jakarta Validation · springdoc-openapi · Lombok · JUnit 5 · Testcontainers · ArchUnit · Docker · GitHub Actions

## Архитектура

Слоистая структура пакетов `com.deliver.shop`:

```
domain          сущности и перечисления (без зависимостей от Spring)
application     сервисы, контракты (DTO), интерфейсы репозиториев и каналов уведомлений, события
infrastructure  реализации уведомлений, асинхронность
api             контроллеры, безопасность, обработка ошибок, конфигурация OpenAPI
```

Зависимости направлены внутрь: `api` и `infrastructure` знают про `application` и `domain`, но не наоборот.
Правила проверяются автоматически тестом `ArchitectureTests` (ArchUnit).

Осознанные компромиссы: JPA-сущности служат моделью предметной области, а репозитории — интерфейсы Spring Data
внутри слоя `application`. Это прагматичный вариант; строгая гексагональная архитектура потребовала бы отдельной
доменной модели и мапперов.

## Запуск

### Всё в Docker

```bash
docker compose up --build
```

Приложение: http://localhost:8080, Swagger UI: http://localhost:8080/swagger-ui.html

Чтобы включить общий лимит входа и регистрации и кэш каталога через Redis:

```bash
docker compose -f docker-compose.yml -f compose.redis.yml up --build
```

Для запуска с Prometheus добавьте `--profile monitoring` перед `up`. Redis доступен
внутри сети Compose. При запуске из IDE можно задать `SPRING_DATA_REDIS_HOST`,
`APP_RATE_LIMIT_REDIS_ENABLED=true` и `APP_CATALOG_CACHE_ENABLED=true` для внешнего Redis.

Секрет для подписи JWT задаётся переменной `JWT_SECRET_KEY` (не короче 32 символов):

```bash
JWT_SECRET_KEY=$(openssl rand -hex 32) docker compose up --build
```

### Локально (IDE)

1. Поднять только базу: `docker compose up postgres` (порт 5433, БД `shop`) — либо использовать свой PostgreSQL.
2. В `application.yaml` по умолчанию `jdbc:postgresql://localhost:5432/dbtest`. Переопределить можно переменными
   `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
3. Запустить `ShopApplication`. Схема создаётся миграциями Flyway в схеме `shop`.

## Мониторинг производительности

```bash
docker compose --profile monitoring up --build -d
```

Prometheus доступен на http://localhost:9090. Собираются время ответа и число HTTP-запросов,
ошибки, JVM/GC/CPU, соединения PostgreSQL и вызовы репозиториев.
При запуске из IDE метрики доступны на http://127.0.0.1:8081/actuator/prometheus.
Служебный порт в Docker не публикуется на хост.

[Метрики, запросы p95/p99 и порядок замеров](monitoring/README.md).

[Нагрузочное тестирование k6: отдельный стенд и готовые сценарии](loadtest/README.md).

## Тесты

Интеграционные тесты поднимают PostgreSQL в Testcontainers, поэтому нужен запущенный Docker (образ `postgres:18`).

```bash
./mvnw verify
```

## Примеры запросов

```bash
# регистрация
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"firstName":"Иван","lastName":"Иванов","email":"ivan@example.com","phone":"+79990001122","password":"Passw0rd!"}'

# вход — в ответе токен
curl -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ivan@example.com","password":"Passw0rd!"}'

# защищённый запрос
curl 'http://localhost:8080/api/orders?page=0&size=20' -H "Authorization: Bearer <token>"
```

История заказов возвращает JSON-массив от новых заказов к старым. Нумерация страниц
начинается с 0, по умолчанию возвращается до 20 заказов, максимум 100 на страницу.
Страница за концом истории возвращает пустой массив.

Точные поля запросов смотрите в Swagger UI.

### Как сделать пользователя администратором

Через API это недоступно намеренно. Выполнить в БД:

```sql
UPDATE shop.users SET role = 'ADMINISTRATOR' WHERE email = 'ivan@example.com';
```

После этого нужно войти заново, чтобы получить токен с новой ролью.

## Принятые решения

- **Flyway вместо `ddl-auto: update`** — схема версионируется, Hibernate только проверяет её (`validate`).
- **Пароли**: PBKDF2WithHmacSHA256, 100 000 итераций, уникальная соль на пользователя.
- **Уведомления**: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` — письмо не уйдёт, если транзакция откатилась,
  и не задерживает ответ клиенту. Email-канал включается, только если задан `spring.mail.host`.
- **Rate limiting** — фиксированное окно, 5 запросов в минуту на IP для `/api/auth/login` и `/api/auth/register`
  (`app.rate-limit.auth-requests-per-minute`). С Redis счётчик общий для экземпляров приложения.
  При ошибке Redis лимит временно применяется на каждом экземпляре отдельно.
  За доверенным прокси нужно настроить определение IP клиента.
- **Кэш каталога** — Redis хранит JSON ответов блюд и категорий 45 секунд (`app.catalog.cache.ttl`).
  Изменение каталога сбрасывает оба набора данных после коммита. При недоступности Redis
  чтение идёт из PostgreSQL. Конкурентное чтение, начавшееся до изменения, может вернуть и
  записать в кэш старую версию на срок TTL. При создании заказа цена и доступность берутся из БД.
- **Ошибки** возвращаются как `application/problem+json`; ошибки валидации — с картой `errors` по полям.

## CI

GitHub Actions (`.github/workflows/ci.yml`) собирает проект и запускает все тесты на каждый push и pull request.
