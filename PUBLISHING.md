# Publishing To RuneLite Plugin Hub

This project is ready for Plugin Hub submission from the code side.

## Final local checklist

1. Confirm the plugin runs locally:
   ```bash
   ./gradlew run
   ```
2. Confirm the project builds cleanly:
   ```bash
   ./gradlew build shadowJar
   ```
3. Confirm `runelite-plugin.properties` has the final metadata you want to publish.
4. Replace `support=` with your GitHub issues URL after the repository exists.

Example:

```properties
support=https://github.com/YOUR_USER/hcim-guide-plugin/issues
```

## Create the GitHub repository

1. Create a new public GitHub repository, for example `hcim-guide-plugin`.
2. Push this project to it.

Example:

```bash
git init
git add .
git commit -m "Initial HCIM Guide plugin"
git branch -M main
git remote add origin https://github.com/YOUR_USER/hcim-guide-plugin.git
git push -u origin main
```

## Submit to Plugin Hub

Official reference:

- https://github.com/runelite/plugin-hub

Steps:

1. Fork `runelite/plugin-hub`.
2. Create a new branch in your fork.
3. In your fork, create a file under `plugins/hcim-guide`.
4. Put only these two lines in that file:

```text
repository=https://github.com/YOUR_USER/hcim-guide-plugin.git
commit=FULL_40_CHARACTER_COMMIT_HASH
```

5. Commit that single file change.
6. Push your branch.
7. Open a pull request against `runelite/plugin-hub`.
8. In the PR description, explain briefly:
   - what the plugin does
   - that it is a guide/helper for the official B0aty HCIM wiki guide
   - that imported wiki-derived content is attributed in `NOTICE.md`
9. Wait for CI and review feedback.
10. If you update your plugin repo after review feedback, update the `commit=` line in `plugins/hcim-guide` to the new full hash and push again.

## Notes for this plugin

- The code license is BSD 2-Clause.
- The bundled guide data is derived from the OSRS Wiki and should keep attribution.
- `icon.png` is already present at the repo root for Plugin Hub display.
- `support=` should point to your issue tracker before submission.
