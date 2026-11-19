# Метрики производительности

Actuator + Micrometer собирают HTTP-метрики, JVM (память, GC, потоки), CPU процесса,
пул соединений HikariCP и вызовы Spring Data repositories. Метрики пула и репозиториев
появляются при наличии соответствующих компонентов; HTTP-серии — после запросов.
Это инструменты измерения: фактические RPS и задержки нужно получить под нагрузкой.

## Запуск

```bash
docker compose --profile monitoring up --build -d
```

Prometheus: http://localhost:9090. В разделе **Status → Target health** цель `shop`
должна быть `UP`. Первое получение метрик может занять 15 секунд после запуска приложения.
Обычный `docker compose up --build` запускает приложение без Prometheus.

При запуске приложения из IDE:

```bash
curl http://127.0.0.1:8081/actuator/health
curl http://127.0.0.1:8081/actuator/metrics
curl http://127.0.0.1:8081/actuator/metrics/http.server.requests
curl http://127.0.0.1:8081/actuator/prometheus
```

Служебный порт `8081` локально привязан к `127.0.0.1`. В Docker он доступен внутри
сети Compose и не публикуется на хост; Prometheus опрашивает `app:8081` без JWT.
Порт API `8080` не предоставляет endpoints Actuator. При развёртывании вне Compose
сохраняйте сетевую изоляцию служебного порта. `/env`, `/configprops` и прочие
служебные endpoints не опубликованы. Детали health скрыты.

## Запросы PromQL

Во всех примерах исключены запросы самого мониторинга: `uri=~"/api/.*"`.
Запросы, отклонённые фильтрами до выбора маршрута (например, 401/429), могут иметь
`uri="UNKNOWN"` и не попадать в эти выборки. Для полного обзора ошибок смотреть
также серии с `uri="UNKNOWN"` и группировку по `status` без фильтра URI.
Задержки выражены в секундах. Для `rate` нужны минимум два опроса; без трафика
часть выражений не возвращает данных. Везде используется метка `application="shop"`.

Число запросов в секунду по маршруту и HTTP-методу:

```promql
sum by (uri, method) (rate(http_server_requests_seconds_count{application="shop",uri=~"/api/.*"}[5m]))
```

p95 задержки по маршруту (для p99 замените `0.95` на `0.99`):

```promql
histogram_quantile(0.95, sum by (le, uri, method) (rate(http_server_requests_seconds_bucket{application="shop",uri=~"/api/.*"}[5m])))
```

Средняя задержка по всему API:

```promql
sum(rate(http_server_requests_seconds_sum{application="shop",uri=~"/api/.*"}[5m]))
/
sum(rate(http_server_requests_seconds_count{application="shop",uri=~"/api/.*"}[5m]))
```

Доля серверных ошибок в процентах (отсутствующая серия ошибок считается нулём):

```promql
100 * (sum(rate(http_server_requests_seconds_count{application="shop",uri=~"/api/.*",status=~"5.."}[5m])) or vector(0))
/
clamp_min(sum(rate(http_server_requests_seconds_count{application="shop",uri=~"/api/.*"}[5m])), 0.000001)
```

Дополнительно:

| Что смотреть | Метрика Prometheus |
| --- | --- |
| CPU процесса, доля от доступного CPU | `process_cpu_usage{application="shop"}` |
| Занятая heap-память, байты | `sum(jvm_memory_used_bytes{application="shop",area="heap"})` |
| Время пауз GC в секунду | `sum(rate(jvm_gc_pause_seconds_sum{application="shop"}[5m]))` |
| Активные соединения PostgreSQL | `hikaricp_connections_active{application="shop"}` |
| Ожидающие соединения потоки | `hikaricp_connections_pending{application="shop"}` |
| Среднее время получения соединения | `rate(hikaricp_connections_acquire_seconds_sum{application="shop"}[5m]) / rate(hikaricp_connections_acquire_seconds_count{application="shop"}[5m])` |
| Вызовы репозиториев, суммарное время | `spring_data_repository_invocations_seconds_sum{application="shop"}` |

Гистограммы HTTP настроены для диапазона 1 мс – 10 с и порогов 100/300/500 мс,
1/3 с. Это границы измерения, а не обещанный SLA. URI размечаются шаблоном маршрута,
например `/api/dishes/{id}`; не добавляйте UUID, email, IP или токены в метки.

## Как получить исходные показатели

1. Запустить приложение и Prometheus; подготовить одинаковый набор категорий и блюд.
2. Прогреть JVM запросами чтения 1–2 минуты.
3. Дать постоянную нагрузку на `/api/categories`, `/api/dishes` и чтение по ID
   на 5–10 минут. Повторить с несколькими уровнями параллелизма.
4. Записать RPS, p95/p99, долю 5xx, CPU, heap, GC и ожидание соединений.
   Для заказов использовать отдельный сценарий с JWT и подготовленными данными.
5. Повторять сценарий с теми же данными и ресурсами после оптимизаций.

При низком CPU и росте ожидания соединений сначала исследовать запросы к БД
и размер пула. При высоком CPU на login — стоимость хеширования паролей.
Метрики репозиториев показывают время вызова метода, а не отдельного SQL;
для поиска N+1 и медленных SQL нужны PostgreSQL EXPLAIN/pg_stat_statements.

Документация: [метрики Spring Boot](https://docs.spring.io/spring-boot/reference/actuator/metrics.html),
[отдельный management-порт](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html).
