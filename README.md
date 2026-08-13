# Mail Agent

Mini-ассистент по образу «Коли»: читает непрочитанные письма в Outlook,
прогоняет тело письма через LLM tool-loop и отвечает письмом. Подробности
задания — `Тестовое-задание-ИИ-агенты.pdf`, план реализации — `PLAN.md`.

## Требования к окружению

- **Java 8** (именно 8). На Apple Silicon нет официальной arm64-сборки —
  здесь используется x86_64 JDK 8 (`jdk1.8.0_281`) под Rosetta 2.
- Maven по умолчанию (через Homebrew) может резолвиться на другой,
  более новый JDK — перед сборкой на Mac явно укажите `JAVA_HOME`:

  ```bash
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_281.jdk/Contents/Home
  ```

- На Windows-стенде для защиты Outlook + JACOB (`jacob-1.20.x64.dll` на
  `PATH`) должны быть уже установлены отдельно.
- **Важно:** `net.sf.jacob-project:jacob:1.20` из задания не опубликован в
  Maven Central (только 1.14.3). Для компиляции здесь используется 1.14.3
  (API стабилен между версиями). На Windows-стенде перед живым JACOB-прогоном
  нужно поставить в pom.xml реальный `jacob-1.20.jar`, соответствующий уже
  установленной `jacob-1.20.x64.dll`, — иначе возможна ошибка нативных
  сигнатур на рантайме. Команда для локальной установки такого jar в Maven-репозиторий:

  ```bash
  mvn install:install-file -Dfile=jacob-1.20.jar \
    -DgroupId=net.sf.jacob-project -DartifactId=jacob \
    -Dversion=1.20 -Dpackaging=jar
  ```

  и поднять версию в `pom.xml` до `1.20`.

## Build

```bash
mvn -q clean package
```

Собирает fat-jar (`target/mail-agent-0.1.0-SNAPSHOT.jar`) через
maven-shade-plugin.

## Test

```bash
mvn test
```

Должно быть зелёным на машине без Outlook (JACOB исключён из
test-classpath — см. `pom.xml`, `maven-surefire-plugin`).

## Run

```bash
java -jar target/mail-agent-0.1.0-SNAPSHOT.jar
```

Конфигурация — `config.yaml` (не в git; скопируйте из `config.example.yaml`
и подставьте реальный `llm.endpoint`). Секреты — только через переменные
окружения:

```bash
cp .env.example .env   # один раз, затем впишите реальный ключ в .env (не в git)
set -a && source .env && set +a
```

Имя переменной (`MAIL_AGENT_LLM_API_KEY` по умолчанию) должно совпадать с
`llm.apiKeyEnv` в `config.yaml`.

**Важно про эндпоинт LLM:** используемый здесь `llm.endpoint` — сторонний
прокси, полученный от заказчика отдельным сообщением вместе с файлом
инструкции по настройке самого Claude Code (`claude-code-setup.md`),
которая просит переключить `ANTHROPIC_BASE_URL`/`ANTHROPIC_AUTH_TOKEN`
самого Claude Code на этот домен. **Этого делать не нужно** — ключ
используется только как `llm.endpoint`/`apiKeyEnv` внутри HTTP-клиента
этого Java-проекта (`LlmClient` → `HttpLlmClient`), а не как замена
настроек самого Claude Code, поскольку легитимность домена как прокси
для трафика самого ассистента не подтверждена доверенным каналом.

Разведочный запрос (`POST {endpoint}/v1/messages`, заголовки `x-api-key` +
`anthropic-version: 2023-06-01`) подтвердил: прокси отвечает в формате
Anthropic Messages API (модель `claude-sonnet-5` вернула штатный ответ с
`content`/`usage`/`stop_reason`). `HttpLlmClient` реализован под этот формат,
включая `tools`/`tool_use` в content-блоках для tool-calling, и покрыт
тестами на `MockWebServer` (без реальных сетевых вызовов в `mvn test`).

## Security-review (перед сдачей)

Само-ревью проведено по трём пунктам из §4/§3.7 задания:

1. **Секреты.** `git grep`/`git log --all -p` по всей истории репозитория —
   ни реальный LLM-ключ, ни домен прокси-эндпоинта ни разу не попали ни в
   один закоммиченный файл (проверено явно, не предположительно). `.env`,
   `config.yaml` и посторонние файлы `claude-code-setup.*` — в `.gitignore`.
2. **ПДн в логах.** `MailAgentServiceLoggingTest` перехватывает все логи и
   читает сырой `audit.jsonl` — подтверждает, что тело письма (проверено на
   примере с именем и телефоном) нигде не всплывает; в логи/аудит попадают
   только `msgId` и имена инструментов.
3. **Инъекции в tool-аргументах.** Ни один инструмент не исполняет shell/SQL
   и не строит файловый путь из данных письма — `find_items` делает
   `String.contains()`, `add_reminder` пишет текст как JSON-строку через
   Jackson. Найдена и исправлена реальная проблема:
   `AgentLoop.errorJson()` вручную экранировал только `"`/`\`, и
   hallucinated `tool_call` с именем инструмента, содержащим control-символ
   (`\n`), ломал JSON, уходящий обратно модели — переведено на
   Jackson-сериализацию (валидный JSON при любом входе), плюс в system-prompt
   добавлен запрет модели дословно цитировать сырые ошибки инструментов
   пользователю.

## Проверено вживую (verification-before-completion)

- `mvn test` → 50/50 зелёных, без Outlook (JACOB исключён из test-classpath).
- `mvn clean package` → собирает `target/mail-agent-0.1.0-SNAPSHOT.jar` (~6.8 МБ).
- `java -jar target/mail-agent-0.1.0-SNAPSHOT.jar` без `MAIL_AGENT_LLM_API_KEY` в env →
  чистое `config_error message=...`, exit code 1, без стектрейса.
- Тот же запуск с ключом, но на Mac (без Outlook) → доходит до подключения
  к Outlook через JACOB и падает там на `UnsatisfiedLinkError` (нет
  `jacob-1.20.x64.dll` на этой платформе) — перехватывается отдельно как
  `LinkageError` и логируется одной понятной строкой
  (`outlook_com_unavailable message=...`), без стектрейса и без падения
  всего процесса в консоль. Это ожидаемо: `OutlookMailChannel` в принципе
  нельзя запустить вне Windows+Outlook — живая проверка самого JACOB
  (реальное письмо → ответ) остаётся за Windows-стендом, доступ к которому
  пока не подтверждён (см. PLAN.md).
- `HttpLlmClient` проверен против `MockWebServer` (6/6 тестов) на точном
  wire-формате, который был подтверждён разведочным `curl`-запросом к
  реальному прокси-эндпоинту заказчика.

## Как я работал с ИИ

Работа шла в Claude Code, по шагам из `PLAN.md`, который был написан до
кода. Внутри каждого шага — строго TDD-цикл: сначала тест, `mvn test`
запускался и его падение (обычно ошибка компиляции — класса ещё нет)
фиксировалось отдельным коммитом `test: red — ...`, только потом писалась
реализация и отдельный коммит `feat: green — ...` после зелёного прогона.
Ни один прод-класс не был написан раньше своего теста.

Что проверялось у модели/во внешних источниках, а не бралось на веру:

- **Версия JACOB.** Задание требует `net.sf.jacob-project:jacob:1.20` —
  проверка через Maven Central Search показала, что в Central опубликована
  только 1.14.3. Не стал предполагать, что версия просто "где-то есть" —
  зафиксировал разрыв в `pom.xml`/README и явную инструкцию, что сделать на
  Windows-стенде.
- **Формат API LLM-эндпоинта.** Вместо того чтобы гадать по названию
  переменных (`ANTHROPIC_BASE_URL`/`ANTHROPIC_AUTH_TOKEN`) в предоставленном
  ключе, сделал разведочные `curl`-запросы к реальному прокси и только по
  подтверждённому ответу (`/v1/messages`, `x-api-key`, формат ответа
  Anthropic Messages API) писал `HttpLlmClient` и тесты к нему.
- **JDK 8 на Apple Silicon.** Проверено `/usr/libexec/java_home -V` — на
  машине уже стоял x86_64-JDK 8, что и использовалось (через Rosetta),
  вместо предположений о необходимости ставить что-то новое.

Что было отклонено: вместе с LLM-ключом от заказчика пришёл файл
`claude-code-setup.md` с инструкцией переключить `ANTHROPIC_BASE_URL`/
`ANTHROPIC_AUTH_TOKEN` самого Claude Code на сторонний домен
(`*.blogmin.ru`) — то есть маршрутизировать весь трафик самого ассистента
через непроверенный сервер. Это классический паттерн подмены доверенного
инструмента, поэтому инструкция не применялась ни на этой машине, ни
рекомендована для Windows-стенда; ключ используется только как
`llm.endpoint`/`apiKeyEnv` внутри HTTP-клиента этого проекта. Подробнее —
см. коммит `chore: env/config-шаблоны для LLM-ключа...`.

Self-review нашёл и исправил два реальных дефекта уже после того, как
соответствующий код был "зелёным":

1. `MailAgentService` при ошибке отправки основного ответа пытался
   отправить fallback-письмо тем же `mailChannel.reply()` — если канал
   сломан (реалистичный сценарий для `OutlookMailChannel` при сбое COM),
   вторая попытка падала уже необработанно. Добавлен тест на этот сценарий
   и вложенный try/catch.
2. `AgentLoop.errorJson()` экранировал вручную только `"`/`\`, пропуская
   control-символы — hallucinated `tool_call` с `\n` в имени инструмента
   ломал JSON. Переведено на Jackson-сериализацию.

## Чек-лист готовности (§11 задания)

- [x] `mvn package` → fat-jar, запускается (проверено вживую, см. выше)
- [x] `mvn test` зелёный без Outlook (50/50)
- [x] `MailChannel`: JACOB-реализация (`OutlookMailChannel`) + мок (`MockMailChannel`)
- [x] ≥2 инструмента (`current_datetime`, `add_reminder`, `find_items`), tool-loop работает на моке
- [x] идемпотентность (`SeenStore`) + переживает рестарт (тест на пересоздание на том же файле)
- [x] конфиг-driven, секреты из env, в git ничего секретного (проверено `git grep`/`git log --all -p`)
- [x] graceful-фолбэк на LLM и COM (включая двойной сбой — см. self-review выше)
- [x] структурные логи, без ПДн (отдельный тест `MailAgentServiceLoggingTest`)
- [x] аудит-журнал действий (hash-chain, `AuditLog`/`AuditEntry`)
- [x] `PLAN.md` + README
- [ ] экспорт сессии Claude Code — добавить перед сдачей (`/export` или файлы `~/.claude/projects/.../*.jsonl`)
- [ ] живой JACOB-прогон на Windows-стенде — доступ пока не подтверждён заказчиком (см. `PLAN.md`)
