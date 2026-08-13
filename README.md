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
`content`/`usage`/`stop_reason`). `HttpLlmClient` будет реализован под этот
формат, включая `tools`/`tool_use` в content-блоках для tool-calling.

## Как я работал с ИИ

_Заполняется по ходу разработки: какие промпты использовались, что
проверялось у модели, что было отклонено._
