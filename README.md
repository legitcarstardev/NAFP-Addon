# NAFP Addons (For Meteor Client)

## Features
- **Better Baritone Build**
  - Add some improvements to the baritone build command
  - Can refill items from any storage block
  - Please use [this fork of baritone](https://github.com/Omega172/baritone-staircase) which fixes staircase building and carpet issues
  - I recommend to use this with [Litematica Printer](https://github.com/aleksilassila/litematica-printer) or [Meteor Litematica Printer](https://github.com/kkllffaa/meteor-litematica-printer)
  - Vide tutorial on how to use it [here](https://youtu.be/mpK_ld8JH34)

## Building
### Local Build
1. Clone the repository.
2. Open the terminal and navigate to the cloned repository.
3. Run the following command to build the project:
   ```bash
   ./gradlew build
   ```
4. The built modules will be located in the `build/libs` directory.

### Github Actions Build
1. Fork the repository.
2. Go into the `Actions` tab of the repository.
3. Go to the `Manual Build and Upload` workflow.
4. Click on `Run Workflow`.
5. Once the workflow is complete, the built modules will be located in the `artifacts` section of the workflow run.


## Contributing
Contributions are welcome! Feel free to open an issue or submit a pull request.

## License
[GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html) - This project is licensed under the GPL-3.0 License - see the [LICENSE](LICENSE) file for details.
