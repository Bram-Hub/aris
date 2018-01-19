PROJECT(Aris C)
cmake_minimum_required(VERSION 2.8.4)
include(CheckIncludeFiles)

set(PACKAGE "aris")
set(VERSION "2.3")
set(PACKAGE_VERSION ${VERSION})
set(PACKAGE_NAME "GNU Aris")
set(PACKAGE_STRING "GNU Aris ${VERSION}")
set(PACKAGE_TARNAME "aris")
set(PACKAGE_BUGREPORT "bug-aris@gnu.org")
set(PACKAGE_URL "http://www.gnu.org/software/aris/")

check_include_files("inttypes.h" HAVE_INTTYPES_H)
check_include_files("memory.h" HAVE_MEMORY_H)
check_include_files("stdint.h" HAVE_STDINT_H)
check_include_files("stdlib.h" HAVE_STDLIB_H)
check_include_files("strings.h" HAVE_STRINGS_H)
check_include_files("string.h" HAVE_STRING_H)
check_include_files("sys/stat.h" HAVE_SYS_STAT_H)
check_include_files("sys/types.h" HAVE_SYS_TYPES_H)
check_include_files("unistd.h" HAVE_UNISTD_H)
check_include_files("assert.h;complex.h;ctype.h;errno.h;fenv.h;float.h;inttypes.h;iso646.h;limits.h;locale.h;math.h;setjmp.h;signal.h;stdarg.h;stdbool.h;stdint.h;stddef.h;stdio.h;stdlib.h;tgmath.h;time.h;wchar.h;wctype.h" STDC_HEADERS)

if(UNIX)
    option(_ALL_SOURCE "Enable _ALL_SOURCE" ON)
    option(_POSIX_PTHREAD_SEMANTICS "Enable _POSIX Solaris" ON)
    option(_GNU_SOURCE "Enable GNU Extensions" ON)
    option(__EXTENSIONS__ "Enable Solaris General Extensions" ON)
endif()

find_package(PkgConfig REQUIRED)
find_package(LibXml2 REQUIRED)
PKG_CHECK_MODULES(GTK3 REQUIRED gtk+-3.0)

file(GLOB SOURCES "aris/*.c")

configure_file(include/config.h.in config.h @ONLY)

include_directories( include/ ${LIBXML2_INCLUDE_DIR} ${GTK3_INCLUDE_DIRS} ${CMAKE_CURRENT_BINARY_DIR})
link_directories(${GTK3_LIBRARY_DIRS})

add_definitions(${GTK3_CFLAGS_OTHER} ${LIBXML2_DEFINITIONS} "-DARIS_GUI")

add_library(aris SHARED ${SOURCES})
add_executable(aris-bin ${SOURCES})

target_link_libraries(aris ${GTK3_LIBRARIES} ${LIBXML2_LIBRARIES} m)
target_link_libraries(aris-bin ${GTK3_LIBRARIES} ${LIBXML2_LIBRARIES} m)

set_target_properties(aris-bin PROPERTIES OUTPUT_NAME aris)
