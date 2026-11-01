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

Секрет для подписи JWT задаётся переменной `JWT_SECRET_KEY` (не короче 32 символов):

```bash
JWT_SECRET_KEY=$(openssl rand -hex 32) docker compose up --build
```

### Локально (IDE)

1. Поднять только базу: `docker compose up postgres` (порт 5433, БД `shop`) — либо использовать свой PostgreSQL.
2. В `application.yaml` по умолчанию `jdbc:postgresql://localhost:5432/dbtest`. Переопределить можно переменными
   `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
3. Запустить `ShopApplication`. Схема создаётся миграциями Flyway в схеме `shop`.

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
curl localhost:8080/api/orders -H "Authorization: Bearer <token>"
```

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
  (`app.rate-limit.auth-requests-per-minute`). Счётчики хранятся в памяти одного экземпляра; для нескольких
  экземпляров нужен общий счётчик (например, Redis), а за прокси — корректное определение IP клиента.
- **Ошибки** возвращаются как `application/problem+json`; ошибки валидации — с картой `errors` по полям.

## CI

GitHub Actions (`.github/workflows/ci.yml`) собирает проект и запускает все тесты на каждый push и pull request.
