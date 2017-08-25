# taskobot
Telegram-бот для создания совместных задач

Сборка: `mvn clean install`

Требуется установленная СУБД PostgreSQL и следующие переменные среды:
* BOT_TOKEN – токен бота, полученный у @BotFather
* BOT_USERNAME – имя бота
* DATABASE_URL – адрес базы данных PostgreSQL, например postgres://user:password@localhost:5432/taskobot
* BOT_IS_WEBHOOK – необязательно. Если значение "1", бот будет получать обновления по протоколу webhook, иначе long-polling. Требует настроенный https на хосте. Следующие переменные среды обязательны только если BOT_IS_WEBHOOK=1.
* PORT – порт сервера
* BOT_EXTERNAL_URL – внешний адрес сервера, например https://example.com
* BOT_INTERNAL_URL – внутренний адрес сервера, например https://localhost
