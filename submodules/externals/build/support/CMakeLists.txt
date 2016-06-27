############################################################################
# CMakeLists.txt
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

cmake_minimum_required(VERSION 3.0)
project(androidsupport LANGUAGES C)

include(GNUInstallDirs)


set(SUPPORT_SOURCES
	"${ANDROID_SUPPORT}/src/musl-locale/iconv.c"
	"${ANDROID_SUPPORT}/src/musl-locale/langinfo.c"
	"${ANDROID_SUPPORT}/src/musl-locale/nl_langinfo_l.c"
	"${ANDROID_SUPPORT}/src/musl-multibyte/wctomb.c"
)

add_library(support STATIC ${SUPPORT_SOURCES})
if(CMAKE_C_COMPILER_ID STREQUAL "GNU")
	target_compile_options(support PRIVATE "-std=c99")
endif()

install(TARGETS support
	RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
	LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
	ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
	PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE GROUP_READ GROUP_EXECUTE WORLD_READ WORLD_EXECUTE
)
