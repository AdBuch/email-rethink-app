# ADR-009: Umschaltbare E-Mail-Verlaufsdarstellungen

- **Status:** Angenommen (Produktentscheidung)
- **Datum:** 2026-11-15
- **Entscheidung:** Offener Punkt 1, Option C
- **Bezug:** `docs/SPEZIFIKATION.md` Abschnitt 5.8 und `docs/konzepte/VERLAUF-BRAINSTORM.md`

## Kontext

Für die Darstellung zusammengehöriger Nachrichten wurden zwei visuelle Konzepte diskutiert: Quipu (verzweigte Zusammenhänge) und Partitur (parallele Verläufe auf einer Zeitachse). Die bisherige Spezifikation sieht eine Darstellung mit unterschiedlichen Zoomstufen vor.

## Entscheidung

1. **Beide Darstellungen werden unterstützt und sind umschaltbar.**
2. **Quipu** zeigt Beziehungen, Abzweigungen und Verknüpfungen innerhalb eines Gesprächs.
3. **Partitur** zeigt zeitlich parallele Nachrichtenstränge und ihre Entwicklung.
4. Für den **MVP** wird zunächst eine einfache, gut lesbare Standardansicht implementiert. Quipu und Partitur folgen inkrementell, ohne die Basisnavigation zu blockieren.
5. Die beiden Darstellungen verwenden dasselbe zugrunde liegende Nachrichten-/Beziehungsmodell; sie erzeugen keine getrennten Datenbestände.
6. Bedienbarkeit, Barrierefreiheit, Performance bei langen Verläufen und mobile Bildschirmgrössen werden mit Prototypen geprüft.
7. Die konkrete Gestaltung der **sechs Linien des Hexagramm-Glyphen** bleibt offen und wird im UI-Prototyp evaluiert. Das ist keine implizite Entscheidung dieses ADR.

## Konsequenzen

Mehr Wahlfreiheit und bessere Darstellung unterschiedlicher Kommunikationsmuster; gleichzeitig höherer UI-/Testaufwand. Die Implementierung wird gestaffelt, damit Phase 0 und MVP nicht von komplexen Visualisierungen abhängig werden.
