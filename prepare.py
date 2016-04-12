#!/usr/bin/env python

############################################################################
# prepare.py
# Copyright (C) 2016  Belledonne Communications, Grenoble France
#
############################################################################
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
#
############################################################################

import argparse
import os
import re
import shutil
import sys
import tempfile
from logging import error, warning, info, INFO, basicConfig
from subprocess import Popen, PIPE
from distutils.spawn import find_executable
sys.dont_write_bytecode = True
sys.path.insert(0, 'submodules/cmake-builder')
try:
    import prepare
except Exception as e:
    error(
        "Could not find prepare module: {}, probably missing submodules/cmake-builder? Try running:\n"
        "git submodule sync && git submodule update --init --recursive".format(e))
    exit(1)


class AndroidTarget(prepare.Target):

    def __init__(self, arch):
        prepare.Target.__init__(self, 'android-' + arch)
        current_path = os.path.dirname(os.path.realpath(__file__))
        self.config_file = 'configs/config-android.cmake'
        self.toolchain_file = 'toolchains/toolchain-android-' + arch + '.cmake'
        self.output = 'liblinphone-sdk/android-' + arch
        self.additional_args = [
            '-DCMAKE_INSTALL_MESSAGE=LAZY',
            '-DLINPHONE_BUILDER_EXTERNAL_SOURCE_PATH=' + current_path + '/submodules'
        ]

    def clean(self):
        if os.path.isdir('WORK'):
            shutil.rmtree('WORK', ignore_errors=False, onerror=self.handle_remove_read_only)
        if os.path.isdir('liblinphone-sdk'):
            shutil.rmtree('liblinphone-sdk', ignore_errors=False, onerror=self.handle_remove_read_only)


class AndroidArmTarget(AndroidTarget):

    def __init__(self):
        AndroidTarget.__init__(self, 'arm')
        self.additional_args += ['-DENABLE_VIDEO=NO']


class AndroidArmv7Target(AndroidTarget):

    def __init__(self):
        AndroidTarget.__init__(self, 'armv7')


class AndroidX86Target(AndroidTarget):

    def __init__(self):
        AndroidTarget.__init__(self, 'x86')

targets = {
    'arm': AndroidArmTarget(),
    'armv7': AndroidArmv7Target(),
    'x86': AndroidX86Target()
}
platforms = ['all', 'arm', 'armv7', 'x86']


class PlatformListAction(argparse.Action):

    def __call__(self, parser, namespace, values, option_string=None):
        if values:
            for value in values:
                if value not in platforms:
                    message = ("invalid platform: {0!r} (choose from {1})".format(
                        value, ', '.join([repr(platform) for platform in platforms])))
                    raise argparse.ArgumentError(self, message)
            setattr(namespace, self.dest, values)


def gpl_disclaimer(platforms):
    cmakecache = 'WORK/android-{arch}/cmake/CMakeCache.txt'.format(arch=platforms[0])
    gpl_third_parties_enabled = "ENABLE_GPL_THIRD_PARTIES:BOOL=YES" in open(
        cmakecache).read() or "ENABLE_GPL_THIRD_PARTIES:BOOL=ON" in open(cmakecache).read()

    if gpl_third_parties_enabled:
        warning("\n***************************************************************************"
                "\n***************************************************************************"
                "\n***** CAUTION, this liblinphone SDK is built using 3rd party GPL code *****"
                "\n***** Even if you acquired a proprietary license from Belledonne      *****"
                "\n***** Communications, this SDK is GPL and GPL only.                   *****"
                "\n***** To disable 3rd party gpl code, please use:                      *****"
                "\n***** $ ./prepare.py -DENABLE_GPL_THIRD_PARTIES=NO                    *****"
                "\n***************************************************************************"
                "\n***************************************************************************")
    else:
        warning("\n***************************************************************************"
                "\n***************************************************************************"
                "\n***** Linphone SDK without 3rd party GPL software                     *****"
                "\n***** If you acquired a proprietary license from Belledonne           *****"
                "\n***** Communications, this SDK can be used to create                  *****"
                "\n***** a proprietary linphone-based application.                       *****"
                "\n***************************************************************************"
                "\n***************************************************************************")


def check_is_installed(binary, prog='it', warn=True):
    if not find_executable(binary):
        if warn:
            error("Could not find {}. Please install {}.".format(binary, prog))
        return False
    return True


def check_tools():
    ret = 0

    # at least FFmpeg requires no whitespace in sources path...
    if " " in os.path.dirname(os.path.realpath(__file__)):
        error("Invalid location: path should not contain any spaces.")
        ret = 1

    ret |= not check_is_installed('cmake')

    if not os.path.isdir("submodules/linphone/mediastreamer2/src") or not os.path.isdir("submodules/linphone/oRTP/src"):
        error("Missing some git submodules. Did you run:\n\tgit submodule update --init --recursive")
        ret = 1

    return ret


def generate_makefile(platforms, generator):
    arch_targets = ""
    for arch in platforms:
        arch_targets += """
{arch}: {arch}-build

{arch}-build:
\t{generator} WORK/android-{arch}/cmake
\t@echo "Done"
""".format(arch=arch, generator=generator)
    makefile = """
archs={archs}
TOPDIR=$(shell pwd)
LINPHONE_ANDROID_VERSION=$(shell git describe --always)
ANDROID_MOST_RECENT_TARGET=$(shell android list target -c | grep -E 'android-[0-9]+' | tail -n1)
ANT_SILENT=$(shell ant -h | grep -q -- -S && echo 1 || echo 0)
PACKAGE_NAME=$(shell sed -nE 's|<property name="linphone.package.name" value="(.*)" />|\\1|p' custom_rules.xml)

.PHONY: all
.NOTPARALLEL: all generate-apk generate-mediastreamer2-apk install release

all: update-project generate-apk

build: $(addsuffix -build, $(archs))

clean: java-clean

install: install-apk run-linphone

java-clean:
\tant clean

$(TOPDIR)/res/raw/rootca.pem:
\tcp liblinphone-sdk/android-{first_arch}/share/linphone/rootca.pem $@

copy-libs:
\trm -rf libs/armeabi
\tif test -d "liblinphone-sdk/android-arm"; then \\
\t\tmkdir -p libs/armeabi && \\
\t\tcp -f liblinphone-sdk/android-arm/lib/lib*-armeabi.so libs/armeabi && \\
\t\tcp -f liblinphone-sdk/android-arm/lib/mediastreamer/plugins/*.so libs/armeabi; \\
\tfi
\tif test -f "liblinphone-sdk/android-arm/bin/gdbserver"; then \\
\t\tcp -f liblinphone-sdk/android-arm/bin/gdbserver libs/armeabi && \\
\t\tcp -f liblinphone-sdk/android-arm/bin/gdb.setup libs/armeabi; \\
\tfi
\trm -rf libs/armeabi-v7a
\tif test -d "liblinphone-sdk/android-armv7"; then \\
\t\tmkdir -p libs/armeabi-v7a && \\
\t\tcp -f liblinphone-sdk/android-armv7/lib/lib*-armeabi-v7a.so libs/armeabi-v7a && \\
\t\tcp -f liblinphone-sdk/android-armv7/lib/mediastreamer/plugins/*.so libs/armeabi-v7a; \\
\tfi
\tif test -f "liblinphone-sdk/android-armv7/bin/gdbserver"; then \\
\t\tcp -f liblinphone-sdk/android-armv7/bin/gdbserver libs/armeabi-v7a && \\
\t\tcp -f liblinphone-sdk/android-armv7/bin/gdb.setup libs/armeabi-v7a; \\
\tfi
\trm -rf libs/x86
\tif test -d "liblinphone-sdk/android-x86"; then \\
\t\tmkdir -p libs/x86 && \\
\t\tcp -f liblinphone-sdk/android-x86/lib/lib*-x86.so libs/x86 && \\
\t\tcp -f liblinphone-sdk/android-x86/lib/mediastreamer/plugins/*.so libs/x86; \\
\tfi
\tif test -f "liblinphone-sdk/android-x86/bin/gdbserver"; then \\
\t\tcp -f liblinphone-sdk/android-x86/bin/gdbserver libs/x86 && \\
\t\tcp -f liblinphone-sdk/android-x86/bin/gdb.setup libs/x86; \\
\tfi

update-project:
\tandroid update project --path . --target $(ANDROID_MOST_RECENT_TARGET)
\tandroid update test-project --path tests -m .

update-mediastreamer2-project:
\t@cd $(TOPDIR)/submodules/linphone/mediastreamer2/java && \\
\tandroid update project --path . --target $(ANDROID_MOST_RECENT_TARGET)

generate-apk: java-clean build copy-libs $(TOPDIR)/res/raw/rootca.pem update-project
\techo "version.name=$(LINPHONE_ANDROID_VERSION)" > default.properties && \\
\tant debug

generate-mediastreamer2-apk: java-clean build copy-libs update-mediastreamer2-project
\t@cd $(TOPDIR)/submodules/linphone/mediastreamer2/java && \\
\techo "version.name=$(LINPHONE_ANDROID_VERSION)" > default.properties && \\
\tant debug

install-apk:
\tant installd

uninstall:
\tadb uninstall $(PACKAGE_NAME)

release: java-clean build copy-libs update-project
\tpatch -p1 < release.patch
\tcat ant.properties | grep version.name > default.properties
\tant release
\tpatch -Rp1 < release.patch

generate-sdk: liblinphone-android-sdk

liblinphone-android-sdk: generate-apk
\tant liblinphone-android-sdk

linphone-android-sdk: generate-apk
\tant linphone-android-sdk

mediastreamer2-sdk: generate-mediastreamer2-apk
\t@cd $(TOPDIR)/submodules/linphone/mediastreamer2/java && \\
\tant mediastreamer2-sdk

liblinphone_tester:
\t$(MAKE) -C liblinphone_tester

run-linphone:
\tant run

run-liblinphone-tests:
\t$(MAKE) -C liblinphone_tester run-all-tests

run-basic-tests: update-project
\tant partial-clean
\t$(MAKE) -C tests run-basic-tests ANT_SILENT=$(ANT_SILENT)

run-all-tests: update-project
\tant partial-clean
\t$(MAKE) -C tests run-all-tests ANT_SILENT=$(ANT_SILENT)


pull-transifex:
\ttx pull -af

push-transifex:
\ttx push -s -f --no-interactive

{arch_targets}

help-prepare-options:
\t@echo "prepare.py was previously executed with the following options:"
\t@echo "   {options}"

help: help-prepare-options
\t@echo ""
\t@echo "(please read the README.md file first)"
\t@echo ""
\t@echo "Available architectures: {archs}"
\t@echo ""
\t@echo "Available targets:"
\t@echo ""
\t@echo "   * all or generate-apk: builds all architectures and creates the linphone application APK"
\t@echo "   * generate-sdk: builds all architectures and creates the liblinphone SDK"
\t@echo "   * install: install the linphone application APK (run this only after generate-apk)"
\t@echo "   * uninstall: uninstall the linphone application"
\t@echo ""
""".format(archs=' '.join(platforms), arch_opts='|'.join(platforms),
           first_arch=platforms[0], options=' '.join(sys.argv),
           arch_targets=arch_targets, generator=generator)
    f = open('Makefile', 'w')
    f.write(makefile)
    f.close()
    gpl_disclaimer(platforms)


def list_features_with_args(debug, additional_args):
    tmpdir = tempfile.mkdtemp(prefix="linphone-android")
    tmptarget = AndroidArmv7Target()
    tmptarget.abs_cmake_dir = tmpdir

    option_regex = re.compile("ENABLE_(.*):(.*)=(.*)")
    options = {}
    ended = True
    build_type = 'Debug' if debug else 'Release'

    for line in Popen(tmptarget.cmake_command(build_type, False, True, additional_args, verbose=False),
                      cwd=tmpdir, shell=False, stdout=PIPE).stdout.readlines():
        match = option_regex.match(line)
        if match is not None:
            (name, typeof, value) = match.groups()
            options["ENABLE_{}".format(name)] = value
            ended &= (value == 'ON')
    shutil.rmtree(tmpdir)
    return (options, ended)


def list_features(debug, args):
    additional_args = args
    options = {}
    info("Searching for available features...")
    # We have to iterate multiple times to activate ALL options, so that options depending
    # of others are also listed (cmake_dependent_option macro will not output options if
    # prerequisite is not met)
    while True:
        (options, ended) = list_features_with_args(debug, additional_args)
        if ended:
            break
        else:
            additional_args = []
            # Activate ALL available options
            for k in options.keys():
                additional_args.append("-D{}=ON".format(k))

    # Now that we got the list of ALL available options, we must correct default values
    # Step 1: all options are turned off by default
    for x in options.keys():
        options[x] = 'OFF'
    # Step 2: except options enabled when running with default args
    (options_tmp, ended) = list_features_with_args(debug, args)
    final_dict = dict(options.items() + options_tmp.items())

    notice_features = "Here are available features:"
    for k, v in final_dict.items():
        notice_features += "\n\t{}={}".format(k, v)
    info(notice_features)
    info("To enable some feature, please use -DENABLE_SOMEOPTION=ON (example: -DENABLE_OPUS=ON)")
    info("Similarly, to disable some feature, please use -DENABLE_SOMEOPTION=OFF (example: -DENABLE_OPUS=OFF)")


def main(argv=None):
    basicConfig(format="%(levelname)s: %(message)s", level=INFO)

    if argv is None:
        argv = sys.argv
    argparser = argparse.ArgumentParser(
        description="Prepare build of Linphone and its dependencies.")
    argparser.add_argument(
        '-ac', '--all-codecs', help="Enable all codecs, including the non-free ones", action='store_true')
    argparser.add_argument(
        '-c', '-C', '--clean', help="Clean a previous build instead of preparing a build.", action='store_true')
    argparser.add_argument(
        '-d', '--debug', help="Prepare a debug build, eg. add debug symbols and use no optimizations.", action='store_true')
    argparser.add_argument(
        '-dv', '--debug-verbose', help="Activate ms_debug logs.", action='store_true')
    argparser.add_argument(
        '--disable-gpl-third-parties', help="Disable GPL third parties such as FFMpeg, x264.", action='store_true')
    argparser.add_argument(
        '--enable-non-free-codecs', help="Enable non-free codecs such as OpenH264, MPEG4, "
        "etc.. Final application must comply with their respective license (see README.md).", action='store_true')
    argparser.add_argument(
        '-f', '--force', help="Force preparation, even if working directory already exist.", action='store_true')
    argparser.add_argument(
        '-G', '--generator', help="CMake build system generator (default: Unix Makefiles, use cmake -h to get the complete list).",
        default='Unix Makefiles', dest='generator')
    argparser.add_argument(
        '-L', '--list-cmake-variables', help="List non-advanced CMake cache variables.", action='store_true', dest='list_cmake_variables')
    argparser.add_argument(
        '-lf', '--list-features', help="List optional features and their default values.", action='store_true', dest='list_features')
    argparser.add_argument(
        '-t', '--tunnel', help="Enable Tunnel.", action='store_true')
    argparser.add_argument('platform', nargs='*', action=PlatformListAction, default=[
                           'arm', 'armv7', 'x86'], help="The platform to build for (default is 'arm armv7 x86'). "
                           "Space separated architectures in list: {0}.".format(', '.join([repr(platform) for platform in platforms])))

    args, additional_args2 = argparser.parse_known_args()

    additional_args = ["-G", args.generator]

    if check_tools() != 0:
        return 1

    if args.debug_verbose is True:
        additional_args += ["-DENABLE_DEBUG_LOGS=YES"]
    if args.enable_non_free_codecs is True:
        additional_args += ["-DENABLE_NON_FREE_CODECS=YES"]
    if args.all_codecs is True:
        additional_args += ["-DENABLE_GPL_THIRD_PARTIES=YES"]
        additional_args += ["-DENABLE_NON_FREE_CODECS=YES"]
        additional_args += ["-DENABLE_AMRNB=YES"]
        additional_args += ["-DENABLE_AMRWB=YES"]
        additional_args += ["-DENABLE_BV16=YES"]
        additional_args += ["-DENABLE_CODEC2=YES"]
        additional_args += ["-DENABLE_G729=YES"]
        additional_args += ["-DENABLE_GSM=YES"]
        additional_args += ["-DENABLE_ILBC=YES"]
        additional_args += ["-DENABLE_ISAC=YES"]
        additional_args += ["-DENABLE_OPUS=YES"]
        additional_args += ["-DENABLE_SILK=YES"]
        additional_args += ["-DENABLE_SPEEX=YES"]
        additional_args += ["-DENABLE_FFMPEG=YES"]
        additional_args += ["-DENABLE_H263=YES"]
        additional_args += ["-DENABLE_H263P=YES"]
        additional_args += ["-DENABLE_MPEG4=YES"]
        additional_args += ["-DENABLE_OPENH264=YES"]
        additional_args += ["-DENABLE_VPX=YES"]
        # additional_args += ["-DENABLE_X264=YES"] # Do not activate x264 because it has text relocation issues
    if args.disable_gpl_third_parties is True:
        additional_args += ["-DENABLE_GPL_THIRD_PARTIES=NO"]

    if args.tunnel or os.path.isdir("submodules/tunnel"):
        if not os.path.isdir("submodules/tunnel"):
            info("Tunnel wanted but not found yet, trying to clone it...")
            p = Popen("git clone gitosis@git.linphone.org:tunnel.git submodules/tunnel".split(" "))
            p.wait()
            if p.returncode != 0:
                error("Could not clone tunnel. Please see http://www.belledonne-communications.com/voiptunnel.html")
                return 1
        warning("Tunnel enabled, disabling GPL third parties.")
        additional_args += ["-DENABLE_TUNNEL=YES", "-DENABLE_GPL_THIRD_PARTIES=OFF"]

    # User's options are priority upon all automatic options
    additional_args += additional_args2

    if args.list_features:
        list_features(args.debug, additional_args)
        return 0

    selected_platforms_dup = []
    for platform in args.platform:
        if platform == 'all':
            selected_platforms_dup += ['arm', 'armv7', 'x86']
        else:
            selected_platforms_dup += [platform]
    # unify platforms but keep provided order
    selected_platforms = []
    for x in selected_platforms_dup:
        if x not in selected_platforms:
            selected_platforms.append(x)

    if os.path.isdir('WORK') and not args.clean and not args.force:
        warning("Working directory WORK already exists. Please remove it (option -C or -c) before re-executing CMake "
                "to avoid conflicts between executions, or force execution (option -f) if you are aware of consequences.")
        if os.path.isfile('Makefile'):
            Popen("make help-prepare-options".split(" "))
        return 0

    for platform in selected_platforms:
        target = targets[platform]

        if args.clean:
            target.clean()
        else:
            retcode = prepare.run(target, args.debug, False, args.list_cmake_variables, args.force, additional_args)
            if retcode != 0:
                return retcode

    if args.clean:
        if os.path.isfile('Makefile'):
            os.remove('Makefile')
    elif selected_platforms:
        # only generated makefile if we are using Ninja or Makefile
        if args.generator.endswith('Ninja'):
            if not check_is_installed("ninja", "it"):
                return 1
            generate_makefile(selected_platforms, 'ninja -C')
            info("You can now run 'make' to build.")
        elif args.generator.endswith("Unix Makefiles"):
            generate_makefile(selected_platforms, '$(MAKE) -C')
            info("You can now run 'make' to build.")
        else:
            warning("Not generating meta-makefile for generator {}.".format(target.generator))

    return 0

if __name__ == "__main__":
    sys.exit(main())
