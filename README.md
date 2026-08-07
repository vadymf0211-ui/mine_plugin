# mine_plugin — Maple Blocks (Клён)

Плагин для Paper 1.21.1 + ресурспак, добавляющие два «чистых» кастомных блока:

| Блок | Предмет | Ванильный BlockState | CustomModelData |
|---|---|---|---|
| **Клён** (Maple Log) | `brown_mushroom_block` | `brown_mushroom_block[up=true,down=true,north=false,south=false,east=false,west=false]` | `1001` |
| **Листва клёна** (Maple Leaves) | `red_mushroom_block` | `red_mushroom_block[up=true,down=true,north=false,south=false,east=false,west=false]` | `1002` |

## Почему именно эти состояния

Комбинация `up=true, down=true, все стороны=false` **никогда не встречается** ни в ванильной генерации гигантских грибов, ни при установке блока игроком (игрок всегда ставит гриб-блок со всеми гранями `true`). Поэтому ресурспак безопасно перекрашивает ровно эти состояния — настоящие грибные блоки выглядят как в ванилле.

## Структура репозитория

```
mine_plugin/
├── pom.xml
├── README.md
├── .gitignore
├── src/
│   └── main/
│       ├── java/com/example/maple/
│       │   ├── MaplePlugin.java      # главный класс
│       │   ├── MapleItems.java       # фабрика кастомных предметов (CMD + PDC)
│       │   ├── MapleBlocks.java      # определения/детект BlockState
│       │   ├── MapleListener.java    # установка, разрушение, взрывы
│       │   └── MapleCommand.java     # /maple give <log|leaves> [amount]
│       └── resources/
│           └── plugin.yml
└── resourcepack/
    ├── pack.mcmeta                   # pack_format 34 (1.21–1.21.1)
    └── assets/minecraft/
        ├── blockstates/
        │   ├── brown_mushroom_block.json
        │   └── red_mushroom_block.json
        ├── models/
        │   ├── block/
        │   │   ├── maple_log.json
        │   │   └── maple_leaves.json
        │   └── item/
        │       ├── brown_mushroom_block.json
        │       └── red_mushroom_block.json
        └── textures/block/
            ├── clen_side.png         # кора клёна (замените на свою)
            ├── clen_top.png          # спил клёна (замените на свою)
            └── clen_leaves.png       # листва клёна (замените на свою)
```

## Сборка плагина

Требуется JDK 21 и Maven:

```bash
mvn clean package
```

Готовый jar: `target/MapleBlocks-1.0.0.jar` → положить в папку `plugins/` сервера Paper 1.21.1.

## Установка ресурспака

Заархивируйте **содержимое** папки `resourcepack/` (так, чтобы `pack.mcmeta` был в корне zip):

```bash
cd resourcepack && zip -r ../MapleBlocks-ResourcePack.zip . && cd ..
```

Затем либо положите zip в `resourcepacks/` клиента, либо раздавайте с сервера через `resource-pack=` в `server.properties`.

## Использование

- `/maple give log [кол-во]` — выдать Клён
- `/maple give leaves [кол-во]` — выдать Листву клёна
- Право: `mapleblocks.give` (по умолчанию op)

## Игровое поведение

- **Клён**: ставится/ломается со звуками дерева (у грибных блоков ванильный звук — дерево), топор ломает быстрее, дроп выпадает всегда (как у ванильных брёвен), без лишнего lore.
- **Листва клёна**: звуки листвы при установке и разрушении, дроп **только ножницами** (как у ванильной листвы).
- Взрывы обрабатываются: клён дропает себя, листва сгорает без дропа (ванильное поведение).
- Идентификация предметов через PersistentDataContainer (ключ `mapleblocks:maple_block`) — надёжнее, чем только CustomModelData.

## Известные ограничения метода

- Скорость разрушения равна скорости грибного блока (hardness 0.2) — блоки ломаются быстрее ванильных брёвен. Это неизбежное свойство метода «занятых BlockState».
- Pick-block (средняя кнопка мыши в креативе) вернёт ванильный грибной блок.
- При установке листвы дополнительно к звуку листвы тихо слышен ванильный звук установки грибного блока (его нельзя отменить со стороны сервера).
