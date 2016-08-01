############################################################################
# CMakeLists.txt
# Copyright (C) 2014  Belledonne Communications, Grenoble France
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
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
#
############################################################################

cmake_minimum_required(VERSION 2.6)
project(SQLITE3 C)


option(ENABLE_STATIC "Build static library (default is shared library)." OFF)


set(SOURCE_FILES sqlite3.c)
if(WIN32)
	list(APPEND SOURCE_FILES sqlite3.def)
endif()

if(CMAKE_SYSTEM_NAME STREQUAL "WindowsPhone" OR CMAKE_SYSTEM_NAME STREQUAL "WindowsStore")
	add_definitions(
		-DSQLITE_OS_WINRT=1
		-DSQLITE_WIN32_FILEMAPPING_API=1
		-DSQLITE_OMIT_LOAD_EXTENSION
	)
endif()

if(ENABLE_STATIC)
	add_library(sqlite3 STATIC ${SOURCE_FILES})
else()
	add_library(sqlite3 SHARED ${SOURCE_FILES})
	if(MSVC)
		if(CMAKE_BUILD_TYPE STREQUAL "Debug")
			install(FILES ${CMAKE_CURRENT_BINARY_DIR}/Debug/sqlite3.pdb
				DESTINATION bin
				PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE GROUP_READ GROUP_EXECUTE WORLD_READ WORLD_EXECUTE
			)
		endif()
	endif()
endif()

install(TARGETS sqlite3
	RUNTIME DESTINATION bin
	LIBRARY DESTINATION lib
	ARCHIVE DESTINATION lib
	PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE GROUP_READ GROUP_EXECUTE WORLD_READ WORLD_EXECUTE
)


file(GLOB HEADER_FILES "*.h")

install(FILES ${HEADER_FILES}
	DESTINATION include
	PERMISSIONS OWNER_READ OWNER_WRITE GROUP_READ WORLD_READ
)
