# epub-converter

## Build the desktop app

Install a JDK, then run the Gradle wrapper command for your operating system from the project root. Build each native package on its target OS:

| Operating system | Command | Package |
| --- | --- | --- |
| macOS | `./gradlew :composeApp:packageDmg` | `.dmg` |
| Windows | `.\gradlew.bat :composeApp:packageMsi` | `.msi` |
| Linux | `./gradlew :composeApp:packageDeb` | `.deb` |

The generated packages are available under `composeApp/build/compose/binaries/main/`.
