#!/bin/bash

set -e

DIR=app/src/main/assets/sing-box
rm -rf $DIR
mkdir -p $DIR
cd $DIR

# GeoIP/GeoSite versions are pinned for reproducible builds.
# The GitHub API rate limit on runners made dynamic "latest release" lookups fail.
# Override with GEOIP_VERSION / GEOSITE_VERSION env vars if needed.

####
VERSION_GEOIP=${GEOIP_VERSION:-20260712}
echo VERSION_GEOIP=$VERSION_GEOIP
echo -n $VERSION_GEOIP > geoip.version.txt
curl -fLSsO https://github.com/SagerNet/sing-geoip/releases/download/$VERSION_GEOIP/geoip.db
xz -9 geoip.db

####
VERSION_GEOSITE=${GEOSITE_VERSION:-20260804102240}
echo VERSION_GEOSITE=$VERSION_GEOSITE
echo -n $VERSION_GEOSITE > geosite.version.txt
curl -fLSsO https://github.com/SagerNet/sing-geosite/releases/download/$VERSION_GEOSITE/geosite.db
xz -9 geosite.db
