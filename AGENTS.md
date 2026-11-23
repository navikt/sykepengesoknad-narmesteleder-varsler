# IntelliJ og Gradle

Bruk IntelliJ MCP og run configurations for formatering, bygg og tester, ikke Gradle i shell. Finn tilgjengelige konfigurasjoner med `get_run_configurations`.

`build` kjører også tester og kan ta flere minutter. Bruk `classes` for kompilering uten tester.

## Opprette en Gradle-konfigurasjon

1. Finn run configurations med `get_run_configurations`. Gradle-oppgaver kan finnes selv om de ikke er listet som run configurations.
2. Mangler konfigurasjonen og MCP ikke kan opprette den, kopier en Gradle-konfigurasjon under `RunManager` i `.idea/workspace.xml`. Gi kopien et nytt navn, sett `taskNames` til ønsket oppgave og fjern uvedkommende `scriptParameters`. Behold eksisterende konfigurasjoner.
3. Åpne `workspace.xml` i IntelliJ og kontroller med `get_run_configurations` at konfigurasjonen er lastet inn. Kjør den med `execute_run_configuration`. Ikke commit lokale IDE-innstillinger.

Ikke bruk IntelliJs terminalverktøy til å omgå begrensninger i shell-sandkassen.
