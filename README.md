# NAFP Addons (for Meteor Client)

NAFP Addons is a small collection of addons for the Meteor Client that extend and improve certain automation features. The main included feature is an improved Baritone build command with smarter refilling behavior.

## Features

- Better Baritone Build
  - Improvements to the Baritone build command for more reliable builds.
  - Can refill items from any storage block.
  - Works well together with Litematica printing tools such as [Litematica Printer](https://github.com/aleksilassila/litematica-printer) or [Meteor Litematica Printer](https://github.com/kkllffaa/meteor-litematica-printer).
  - Video tutorial on how to use it: https://youtu.be/mpK_ld8JH34

## Installation

1. Build locally (see "Building" below) or download a prebuilt module from the repository actions artifacts if available.
2. Place the built `.jar` files (modules) into your Meteor client's mods/plugins folder or follow your launcher/modloader's instructions for adding client-side mods.

Note: exact installation steps vary depending on your Meteor Client setup and Minecraft launcher — adapt accordingly.

## Building

### Local Build

1. Clone the repository.
2. Open a terminal and navigate to the cloned repository.
3. Run:

```bash
./gradlew build
```

4. Built modules will be placed in `build/libs`.

### GitHub Actions Build

1. Fork the repository.
2. Open the `Actions` tab on your fork.
3. Find the "Manual Build and Upload" workflow and click "Run workflow".
4. After the workflow completes, download the built modules from the workflow's `artifacts` section.

## Usage

- Use the Better Baritone Build feature when running Baritone build jobs to allow automatic refilling of required items from nearby storage blocks.
- For best results when printing schematics, pair this addon with a Litematica printer implementation (links above).

## Contributing

Contributions are welcome! If you'd like to contribute:

1. Open an issue to discuss larger changes before investing time in implementation.
2. Fork the repository and create a feature branch.
3. Submit a pull request with a clear description of your changes.

Please keep behavior backwards-compatible and include tests or manual verification steps when relevant.

## Troubleshooting

- If the build fails locally, ensure you have a compatible Java version and Gradle wrapper available. Run `./gradlew --version` to check.
- If the addon does not load in Meteor Client, verify you placed the `.jar` in the correct mods/plugins folder and that the Meteor Client version you run is compatible.

## License

This project is licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html). See the [LICENSE](LICENSE) file for details.

## Contact

If you have questions or want to report bugs, open an issue in this repository.
