-- Выполнять только в отдельной БД нагрузочного стенда, после миграций Flyway.
BEGIN;
INSERT INTO shop.categories (id, name)
SELECT md5('loadtest-category-' || n)::uuid, 'Категория ' || n
FROM generate_series(1, 20) AS n
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop.dishes (id, name, description, structure, price, is_available, category_id)
SELECT md5('loadtest-dish-' || n)::uuid, 'Блюдо ' || n,
       'Описание тестового блюда', 'Тестовый состав', 100 + n % 500, true,
       md5('loadtest-category-' || (1 + (n - 1) % 20))::uuid
FROM generate_series(1, 500) AS n
ON CONFLICT (id) DO NOTHING;
COMMIT;
ANALYZE shop.categories;
ANALYZE shop.dishes;
