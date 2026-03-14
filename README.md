# MultiTool

MultiTool ist ein Minecraft-Plugin fuer Paper/Spigot-Server auf Basis der 1.21-API, das ein verzaubertes Holz-Regal in ein intelligentes Universalwerkzeug verwandelt. Das Item heisst `Multitool`, speichert mehrere Werkzeuge direkt in seinem eigenen Inventar und waehlt automatisch das passende Werkzeug oder die passende Waffe aus, je nachdem, worauf der Spieler schaut.

Das Plugin ist fuer Spieler gedacht, die nicht staendig zwischen Spitzhacke, Axt, Harke, Schwert, Bogen, Angel oder anderen Werkzeugen wechseln wollen. Statt viele einzelne Items in der Hotbar mitzuschleppen, wird alles in einem zentralen Item gebuendelt. Das Multitool kann ausserdem um ein Totem of Undying und ein Buch mit Fluch der Bindung erweitert werden.

## Was das Plugin macht

Das Multitool ist ein spezielles Item, das optisch auf einem Holz-Regal basiert. Im Inneren besitzt es eigene Upgrade-Slots fuer verschiedene Werkzeuge und Sonderfunktionen. Sobald der Spieler das Multitool in der Hand haelt, prueft das Plugin laufend, welches Ziel gerade angeschaut wird, und schaltet das Item automatisch auf das am besten passende Werkzeug um.

Beispiele:

- Blick auf Stein oder Erze: Das Multitool verwendet die gespeicherte Spitzhacke.
- Blick auf Holz oder Baumstamm: Das Multitool verwendet die gespeicherte Axt.
- Blick auf Erde, Sand oder aehnliche Bloecke: Das Multitool verwendet die gespeicherte Schaufel.
- Blick auf Laub, Pflanzen oder Farmland: Das Multitool verwendet die gespeicherte Harke.
- Nahkampf gegen feindliche Mobs: Das Multitool verwendet das gespeicherte Schwert.
- Friedliche Tiere in der Naehe: Das Multitool verwendet den gespeicherten Speer.
- Ziele in groesserer Entfernung: Das Multitool verwendet je nach Situation Bogen oder Speer.
- Blick auf Wasser oder Wasserlebewesen: Das Multitool verwendet die gespeicherte Angel.

Wenn ein gespeichertes Werkzeug nur noch `1` Haltbarkeit uebrig hat, wird es automatisch nicht mehr ausgewaehlt. So wird vermieden, dass ein wichtiges Werkzeug unabsichtlich zerbricht. Falls kein verwendbares Werkzeug mehr gespeichert ist, bleibt nur noch das Regal selbst aktiv.

## Hauptfunktionen

- Ein einziges Item speichert mehrere Werkzeuge gleichzeitig.
- Automatische Werkzeugwahl anhand des aktuell anvisierten Blocks oder Mobs.
- Werkzeug-Upgrades koennen direkt im Multitool-Menue eingelegt und wieder entnommen werden.
- Das Multitool behaelt die Holzfarbe des verwendeten Shelf-Regals.
- Ein gespeichertes Totem kann beim Tod automatisch ausgeloest werden.
- Ein gespeichertes Buch mit Fluch der Bindung verhindert das Droppen oder Ablegen in Container.
- Fast defekte Werkzeuge werden automatisch deaktiviert, bevor sie zerbrechen.
- Ein Settings-Menue erlaubt die Bevorzugung bestimmter Werkzeuge fuer verschiedene Mob-Zieltypen.

## Crafting

### Rezept fuer das Multitool

Das Multitool wird shapeless im Plugin verarbeitet, erwartet aber die Zutaten im Raster wie im Bild:

| Axt | Schaufel | Holzspeer |
| --- | --- | --- |
| Angel | Regal | Bogen |
| Holzschwert | Spitzhacke | Harke |

Verwendete Zutaten:

- `Wooden Axe`
- `Wooden Shovel`
- `WOODEN_SPEAR`
- `Fishing Rod`
- ein Holz-`SHELF`
- `Bow`
- `Wooden Sword`
- `Wooden Pickaxe`
- `Wooden Hoe`

Wichtiger Hinweis zum aktuellen Stand:

- Das Plugin verwendet die 1.21-Holzregale als echte `*_SHELF`-Items.
- Dadurch uebernimmt das Multitool direkt die Farbe beziehungsweise Holzart des verwendeten Regals.
- Unterstuetzt werden alle normalen Holzvarianten sowie Crimson und Warped.
- Fuer den Speer wird kein Custom-Item erzeugt. Das Rezept erwartet den nativen `WOODEN_SPEAR` der 1.21-API.

## Wie man das Multitool benutzt

### 1. Multitool herstellen

Besorge zuerst einen nativen `WOODEN_SPEAR` auf deinem Server. Danach platziere alle benoetigten Gegenstaende im Crafting-Feld entsprechend dem Rezept. Das Ergebnis ist ein verzaubertes Regal mit dem Namen `Multitool`.

### 2. Multitool in die Hand nehmen

Sobald das Multitool in der Haupthand liegt, beginnt die automatische Auswahl des passenden Werkzeugs. Je nachdem, was der Spieler anschaut, aendert das Item intern seinen aktiven Zustand.

### 3. Hauptmenue oeffnen

Mit `Ducken + Rechtsklick` wird das Hauptmenue des Multitools geoeffnet.

Im Menue:

- In der Mitte befindet sich das Regal, also das Multitool selbst.
- Rundherum liegen die verschiedenen Werkzeug-Slots.
- Jeder Werkzeug-Slot fuehrt in ein eigenes Upgrade-Menue.
- Ein Klick auf das mittlere Regal oeffnet das Upgrade-Menue fuer das Multitool selbst.
- Ein weiterer Button oeffnet das Settings-Menue fuer die Auto-Auswahl.

### 4. Werkzeuge einsetzen oder entnehmen

Jedes Werkzeug besitzt ein eigenes Upgrade-Menue. Dort kann genau das passende Werkzeug eingelegt werden, zum Beispiel:

- Axt-Slot fuer Aexte
- Spitzhacken-Slot fuer Spitzhacken
- Harken-Slot fuer Harken
- Schwert-Slot fuer Schwerter
- Bogen-Slot fuer Boegen
- Angel-Slot fuer Angeln
- Speer-Slot fuer Speere

Das eingelegte Werkzeug darf Verzauberungen besitzen. Es bleibt im Multitool gespeichert, bis es wieder aus dem Slot herausgenommen wird.

Werkzeuge koennen jederzeit wieder entfernt werden:

- Menue oeffnen
- entsprechenden Slot aufrufen
- Werkzeug aus dem Slot herausnehmen
- Inventar schliessen oder ueber den Zurueck-Button ins Hauptmenue gehen

### 5. Regal-Upgrades verwenden

Das mittlere Regal besitzt ein eigenes Upgrade-Menue mit zwei Zusatz-Slots:

- `Totem-Slot`
- `Bindungsbuch-Slot`

Auch diese Items sind wieder entnehmbar.

#### Totem-Slot

In diesen Slot kann ein `Totem of Undying` gelegt werden.

Verhalten:

- Wenn der Spieler sterben wuerde und im Inventar ein Multitool mit gespeichertem Totem besitzt, wird das Totem automatisch verbraucht.
- Das Multitool loest dann den normale Totem-Effekt aus.
- Liegen mehrere Totems im Slot, wird jeweils nur eines verbraucht.

#### Bindungsbuch-Slot

In diesen Slot kann ein `Enchanted Book` mit `Fluch der Bindung` gelegt werden.

Verhalten:

- Das Multitool kann nicht mehr normal gedroppt werden.
- Das Multitool kann nicht mehr in geoeffnete Container verschoben oder abgelegt werden.
- Das Buch kann spaeter wieder aus dem Slot entfernt werden, um die Sperre aufzuheben.

### 6. Settings fuer die Auto-Auswahl

Im Settings-Menue kann festgelegt werden, welches Werkzeug bei bestimmten Zieltypen bevorzugt wird.

Aktuell einstellbar:

- feindliche Mobs in der Naehe
- feindliche Mobs in der Ferne
- friedliche Mobs in der Naehe
- friedliche Mobs in der Ferne
- Wasser-Mobs
- unbekannte Ziele in der Naehe
- unbekannte Ziele in der Ferne

Bedienung:

- Linksklick auf einen Setting-Eintrag: naechstes Werkzeug
- Rechtsklick auf einen Setting-Eintrag: vorheriges Werkzeug

Beispiele:

- Friedliche Tiere nah: Speer, Schwert, Axt oder Bogen
- Feindliche Mobs nah: Schwert, Axt, Speer oder Bogen
- Feindliche Mobs fern: Bogen, Speer, Schwert oder Axt

## Automatische Werkzeugauswahl im Detail

Die automatische Erkennung orientiert sich am anvisierten Ziel.

### Block-Erkennung

- Holz, Stamme und aehnliche Holzbloecke: Axt
- Stein, Erze und pickaxe-typische Bloecke: Spitzhacke
- Erde, Sand, Schnee und shovel-typische Bloecke: Schaufel
- Laub, Pflanzen, Farmland und hoe-typische Bloecke: Harke
- Wasser oder wasserbezogene Ziele: Angel

### Mob-Erkennung

- Feindliche Mobs in der Naehe: Schwert
- Friedliche Tiere in der Naehe: Speer
- Ziele in groesserer Entfernung: Bogen
- Wasserlebewesen: Angel

Die Auswahl ist regelbasiert. Wenn fuer eine erkannte Situation kein passendes, noch haltbares Werkzeug gespeichert ist, faellt das Multitool auf das Regal zurueck oder verwendet ein anderes verfuegbares Werkzeug nur dann, wenn dafuer eine direkte Regel vorhanden ist.

## Haltbarkeit und Sicherheit

Das Plugin uebernimmt die Haltbarkeit des aktiven, gespeicherten Werkzeugs direkt auf das Multitool. Dadurch verhaelt sich das Item beim Benutzen wie das echte Werkzeug.

Sobald ein gespeichertes Werkzeug nur noch `1` Haltbarkeit uebrig hat:

- wird es nicht mehr automatisch ausgewaehlt
- bleibt im Multitool gespeichert
- kann im Menue entnommen, repariert oder ersetzt werden

Das verhindert, dass Werkzeuge im normalen Einsatz versehentlich zerbrechen.

## Technische Hinweise

- Projektformat: Maven
- Zielplattform: Paper/Spigot 1.21.x API
- Java-Version: 17
- Das Plugin wurde so gebaut, dass es als normales Server-Plugin in den `plugins`-Ordner gelegt werden kann.

## Installation

1. Die erzeugte Jar-Datei in den `plugins`-Ordner des Servers legen.
2. Server starten oder neu laden.
3. Das Multitool im Spiel craften und konfigurieren.

Die gebaute Datei liegt nach dem Maven-Build in:

- `target/multitool-1.0.0.jar`

## Zusammenfassung

MultiTool kombiniert mehrere Werkzeuge, Waffen und Zusatzfunktionen in einem einzigen Gegenstand. Spieler erhalten damit ein flexibles System fuer Mining, Farming, Kampf und Utility, ohne dauernd die Hotbar umsortieren zu muessen. Durch die internen Upgrade-Slots, die automatische Werkzeugwahl, die Totem-Sicherung und das Bindungs-Upgrade eignet sich das Plugin besonders fuer Survival-Server mit Fokus auf Komfort und Inventar-Management.
