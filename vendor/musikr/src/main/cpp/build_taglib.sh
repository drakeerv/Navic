set -e
WORKING_DIR=$1
echo "Working directory is at $WORKING_DIR"
cd "$WORKING_DIR"

JOBS="$(nproc 2> /dev/null || sysctl -n hw.ncpu 2> /dev/null || echo 4)"
echo "Using $JOBS jobs"
TAGLIB_SRC_DIR=${WORKING_DIR}/taglib
TAGLIB_DST_DIR=${WORKING_DIR}/taglib/build
TAGLIB_PKG_DIR=${WORKING_DIR}/taglib/pkg
NDK_TOOLCHAIN=${WORKING_DIR}/android.toolchain.cmake
NDK_PATH=$2
# ndk lives at $SDK/ndk/<version>, fall back to it since ANDROID_HOME may be unset
SDK_DIR=${ANDROID_HOME:-$(cd "$NDK_PATH/../.." && pwd)}
CMAKE_ROOT=${SDK_DIR}/cmake
if [ ! -d "$CMAKE_ROOT" ]; then
	echo "Error: $CMAKE_ROOT does not exist" >&2
	exit 1
fi

CMAKE_VERSION_DIR=""
for dir in $(find "$CMAKE_ROOT" -mindepth 1 -maxdepth 1 -type d | sort); do
	CMAKE_VERSION_DIR=${dir##*/}
	break
done

if [ -z "$CMAKE_VERSION_DIR" ]; then
	echo "Error: No CMake version found in $CMAKE_ROOT" >&2
	exit 1
fi
CMAKE_BIN="${CMAKE_ROOT}/${CMAKE_VERSION_DIR}/bin/cmake"

echo "Taglib source is at $TAGLIB_SRC_DIR"
echo "Taglib build is at $TAGLIB_DST_DIR"
echo "Taglib package is at $TAGLIB_PKG_DIR"
echo "NDK toolchain is at $NDK_TOOLCHAIN"
echo "NDK path is at $NDK_PATH"

X86_ARCH=x86
X86_64_ARCH=x86_64
ARMV7_ARCH=armeabi-v7a
ARMV8_ARCH=arm64-v8a

build_for_arch() {
  local ARCH=$1
  local DST_DIR=$TAGLIB_DST_DIR/$ARCH
  local PKG_DIR=$TAGLIB_PKG_DIR/$ARCH

  cd $TAGLIB_SRC_DIR
  "$CMAKE_BIN" -B $DST_DIR -DANDROID_NDK_PATH=${NDK_PATH} -DCMAKE_TOOLCHAIN_FILE=${NDK_TOOLCHAIN}  \
    -DANDROID_ABI=$ARCH -DBUILD_SHARED_LIBS=OFF -DVISIBILITY_HIDDEN=ON -DBUILD_TESTING=OFF \
    -DBUILD_EXAMPLES=OFF -DBUILD_BINDINGS=OFF -DWITH_ZLIB=OFF -DCMAKE_BUILD_TYPE=MinSizeRel \
    -DWITH_APE=OFF -DWITH_ASF=OFF -DWITH_DSF=OFF -DWITH_MATROSKA=OFF -DWITH_MOD=OFF \
    -DWITH_SHORTEN=OFF -DWITH_TRUEAUDIO=OFF \
    -DCMAKE_CXX_FLAGS="-fPIC -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden"
  "$CMAKE_BIN" --build $DST_DIR --config MinSizeRel -j$JOBS
  cd $WORKING_DIR

  "$CMAKE_BIN" --install $DST_DIR --config MinSizeRel --prefix $PKG_DIR --strip
}

build_for_arch $X86_ARCH
build_for_arch $X86_64_ARCH
build_for_arch $ARMV7_ARCH
build_for_arch $ARMV8_ARCH
