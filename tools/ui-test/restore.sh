#!/bin/sh
# Put a configuration backed up from the test device back and restart Vulcan.
# Usage: ./restore.sh [backup-dir]   (default: ./backup-prefs; expects shared_prefs/*.xml and files/*)
set -e
PKG=dev.vulcanlauncher
DIR=${1:-$(cd "$(dirname "$0")" && pwd)/backup-prefs}
[ -d "$DIR/shared_prefs" ] || { echo "no shared_prefs in $DIR" >&2; exit 1; }
adb shell "am force-stop $PKG"
for f in "$DIR"/shared_prefs/*.xml; do
    name=$(basename "$f")
    adb shell "run-as $PKG sh -c 'cat > /data/data/$PKG/shared_prefs/$name'" < "$f"
done
for f in "$DIR"/files/*; do
    [ -e "$f" ] || continue
    name=$(basename "$f")
    adb shell "run-as $PKG sh -c 'cat > /data/data/$PKG/files/$name'" < "$f"
done
adb shell "am start -n $PKG/.MainActivity" >/dev/null
echo "Restored $DIR and restarted Vulcan."
