#!/bin/bash
set -e

source "buildScript/init/env.sh"
ENV_NB4A=1
source "buildScript/lib/core/get_source_env.sh"

# Build dependencies are cloned inside the repository (and gitignored) so that
# nothing has to live next to the checkout. libcore/go.mod replaces resolve to
# these paths.
VENDOR_DIR="deps"

####

mkdir -p "$VENDOR_DIR"

if [ ! -d "$VENDOR_DIR/sing-box" ]; then
  git clone --no-checkout https://github.com/sstpnk/sing-box.git "$VENDOR_DIR/sing-box"
fi
pushd "$VENDOR_DIR/sing-box"
git checkout "$COMMIT_SING_BOX"
popd

####

if [ ! -d "$VENDOR_DIR/libneko" ]; then
  git clone --no-checkout https://github.com/MatsuriDayo/libneko.git "$VENDOR_DIR/libneko"
fi
pushd "$VENDOR_DIR/libneko"
git checkout "$COMMIT_LIBNEKO"
popd

####

