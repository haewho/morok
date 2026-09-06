#!/bin/sh
# Local source build only. Does not install services or deploy to a server.
set -eu
morok_dest=${1:?Pass a new output directory}
morok_sha=dc81f7981c6b3cf132341204f0310662826826a0
test ! -e "$morok_dest"
git clone --no-checkout https://github.com/9seconds/mtg.git "$morok_dest"
git -C "$morok_dest" checkout --detach "$morok_sha"
test "$(git -C "$morok_dest" rev-parse HEAD)" = "$morok_sha"
cd "$morok_dest"
GOTOOLCHAIN=local go version
GOTOOLCHAIN=local go mod verify
GOTOOLCHAIN=local go build -trimpath -o morok-mtg .
shasum -a 256 morok-mtg > morok-mtg.sha256
