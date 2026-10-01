# Repository Guidelines

## Project Structure

- `src/main/java/dev/storagecleanup/Main.java` is the Java 17 Swing entry point and builds the primary scan screen.
- `src/main/java/dev/storagecleanup/presentation/homebrew/` contains the Homebrew panel and table model.
- `src/main/java/dev/storagecleanup/presentation/models/` contains Ollama and Hugging Face panels and table models.
- `src/main/java/dev/storagecleanup/presentation/support/` contains the Application Support panel and table model.
- `src/main/java/dev/storagecleanup/presentation/files/` contains the candidate table model and allocation bar.
- `src/main/java/dev/storagecleanup/domain/` contains the application data records.
- `src/main/java/dev/storagecleanup/infrastructure/` contains filesystem and macOS integration adapters.
- `README.md` documents scan behavior and how to launch the app.
- `run-mac-storage-cleaner.command` is the macOS Finder launcher.
- `pom.xml` defines the Maven build. There is currently no test source tree or checked-in asset directory.

## Build and Run

- `./run.command` compiles and launches the application with Java 17 or newer.
- `mvn package` compiles the project and builds a runnable JAR under `target/` (Maven is optional for direct launch).
- `mvn test` runs Maven tests if tests are added; no test framework or test suite is currently configured.

## Coding Style

Use Java 17 features and four-space indentation. Keep each feature's UI and event handling in its panel class; put longer filesystem or process work in `SwingWorker` so the Swing event thread stays responsive. Use `UpperCamelCase` for types, `lowerCamelCase` for methods and fields, and descriptive names for paths and package operations. Prefer standard-library APIs and avoid adding dependencies without a clear need.

Keep classes under 500 lines. If a class grows beyond this, it is likely doing too much - split it into focused classes, each with a single clear responsibility.

## Testing Guidelines

There are no existing automated tests or coverage requirements. For changes, at minimum compile with `mvn package` (or `javac --release 17`) and manually check affected UI flows on macOS. Exercise scanner and Homebrew features against safe sample data; do not uninstall real packages as a test.

## Safety and Configuration

File cleanup must remain user-directed and use the macOS Trash. Keep explicit confirmation before Homebrew uninstall operations, pass arguments directly to `ProcessBuilder` rather than through a shell, and handle missing Homebrew or inaccessible files gracefully. Never hard-code user-specific paths; derive home paths from `user.home`.

## Commits and Pull Requests

No Git history or established commit convention is available in this checkout. Use a short imperative commit subject (for example, `Add package list refresh`). Pull requests should explain the user-visible change, list verification performed, and include a screenshot for UI changes. Link a related issue when one exists.
