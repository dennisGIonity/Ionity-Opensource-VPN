// Runs the Gradle wrapper on any OS (gradlew.bat on Windows, ./gradlew elsewhere).
// Lesson carried over from Gate^Flame: a POSIX "cd android && ./gradlew" npm script
// silently goes to cmd.exe on Windows and fails there.
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const dir = fileURLToPath(new URL('../android', import.meta.url));
const win = process.platform === 'win32';
const r = spawnSync(win ? 'gradlew.bat' : './gradlew', process.argv.slice(2), { cwd: dir, stdio: 'inherit', shell: win });
process.exit(r.status ?? 1);
