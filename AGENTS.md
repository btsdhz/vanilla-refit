# Repository Guidelines

This is a NeoForge 1.21.1 Minecraft mod (mod id `btsdhz_original`, Java 21, Gradle). It adds stairs, slabs, and their vertical variants, and reuses data generation to emit blockstates and per-material models.

## Language & Communication

When the user writes in Chinese, reply in Chinese and keep any internal reasoning in Chinese as well. If the user switches to English, respond in English. Keep explanations concise and non-technical unless the user asks for more detail.

## Project Structure

- `src/main/java/com/example/myfirstmod/` — Java sources. `BtsdhzOriginal.java` is the entrypoint, `ModBlocks.java` registers blocks/items, `datagen/` holds the data generators, `mixin/` and `util/` hold mixins and shared helpers.
- `src/main/resources/assets/btsdhz_original/` — mod assets (`blockstates/`, `models/block/`, `textures/`, `lang/`, `recipe/`). Namespace is `btsdhz_original`.
- `src/main/resources/assets/minecraft/` — vanilla block/blockstate overrides.
- `src/generated/resources/` — data-generator output; regenerate it, never hand-edit.
- `models/block/` — hand-authored base templates (e.g. `vertical_stair.json`, `vertical_stair_conn_left.json`) plus generated per-material variants (e.g. `vertical_stair_conn_right_oak_stairs.json`).

## Build, Test, and Development Commands

- `gradlew build` — compile and package the mod jar into `build/libs/`.
- `gradlew runClient` — start the client dev environment.
- `gradlew runServer` — start a headless dev server.
- `gradlew runData` — regenerate blockstates and per-material models into `src/generated/resources/`.
- `gradlew runGameTestServer` — run registered GameTest framework tests, then exit.
- `gradlew clean` or `gradlew --refresh-dependencies` — reset build or dependency cache.
- After editing hand-authored model templates, run `python clean_models.py` (in `models/block/`) then `gradlew runData` to regenerate the derived files.

## Coding Style & Naming Conventions

- Java 21, UTF-8, standard 4-space indentation; no formatter/linter is configured, so match the surrounding files.
- `mod_id` is lowercase snake_case (`btsdhz_original`); Java package prefix is `com.example.myfirstmod`.
- Registry ids are lowercase snake_case (`smooth_stone_stairs`), registered through `DeferredRegister` in `ModBlocks`.
- Assets follow Minecraft paths: `models/block/<id>.json`, `blockstates/<id>.json`, `textures/block/<id>.png`. Per-material model ids use `<base>_<material>`.
- Custom model UV maps must stay consistent with the base templates (each face samples the portion of the 16×16 texture it covers); geometry changes require re-running `runData`.
- New block-state properties must resolve to the vanilla look when the property is absent: booleans use `util/DefaultFalseBooleanProperty`, enums put the vanilla form first. Missing properties are decoded as the first allowed value, so the wrong order makes existing worlds change shape when the mod is added mid-save.

## Testing Guidelines

Use NeoForge's GameTest framework; there is no JUnit suite. The client/server/data run configs already scope tests to `btsdhz_original`. Verify with `gradlew runGameTestServer`, or manually place blocks via `gradlew runClient`. No coverage threshold is enforced.

Do not package or hand over jars (`build/libs/*.jar`) unless the user explicitly asks — by default "done" means the code compiles and `gradlew runData` / `gradlew build` pass; the user verifies behaviour in-game themselves. Also avoid starting a second `runClient` while the user's client is open.

## Commit & Pull Request Guidelines

The repository is initialized as a Git repo. All subsequent code changes must be committed with standardized Conventional Commit messages — this is a hard requirement at every key milestone, not a suggestion.

Commit format: `<type>(<scope>): <subject>`. `type` is one of `feat`, `fix`, `refactor`, `docs`, `test`, `chore`; `scope` is optional (e.g. `block`, `datagen`, `model`); `subject` is a short imperative summary under 72 characters.

Examples:

- `feat(block): add vertical stair connector models`
- `fix(model): correct UV mapping on stair connectors`
- `refactor(datagen): simplify blockstate generation`
- `docs: update this guide`
- `chore(datagen): regenerate blockstates and models`

Rules:

- Commit one logical change per commit, at meaningful checkpoints — never bundle unrelated work.
- Stage only relevant files (`git add <paths>`), and review `git status` before committing.
- Run `gradlew build` and `gradlew runData` before committing.
- A pull request should describe what changed and why, note affected blocks/models, and include a screenshot for visual/asset changes.
