# Install script for directory: /home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/src

# Set the install prefix
if(NOT DEFINED CMAKE_INSTALL_PREFIX)
  set(CMAKE_INSTALL_PREFIX "/usr/local")
endif()
string(REGEX REPLACE "/$" "" CMAKE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}")

# Set the install configuration name.
if(NOT DEFINED CMAKE_INSTALL_CONFIG_NAME)
  if(BUILD_TYPE)
    string(REGEX REPLACE "^[^A-Za-z0-9_]+" ""
           CMAKE_INSTALL_CONFIG_NAME "${BUILD_TYPE}")
  else()
    set(CMAKE_INSTALL_CONFIG_NAME "Debug")
  endif()
  message(STATUS "Install configuration: \"${CMAKE_INSTALL_CONFIG_NAME}\"")
endif()

# Set the component getting installed.
if(NOT CMAKE_INSTALL_COMPONENT)
  if(COMPONENT)
    message(STATUS "Install component: \"${COMPONENT}\"")
    set(CMAKE_INSTALL_COMPONENT "${COMPONENT}")
  else()
    set(CMAKE_INSTALL_COMPONENT)
  endif()
endif()

# Install shared libraries without execute permission?
if(NOT DEFINED CMAKE_INSTALL_SO_NO_EXE)
  set(CMAKE_INSTALL_SO_NO_EXE "1")
endif()

# Is this installation the result of a crosscompile?
if(NOT DEFINED CMAKE_CROSSCOMPILING)
  set(CMAKE_CROSSCOMPILING "FALSE")
endif()

# Set path to fallback-tool for dependency-resolution.
if(NOT DEFINED CMAKE_OBJDUMP)
  set(CMAKE_OBJDUMP "/usr/bin/objdump")
endif()

if(CMAKE_INSTALL_COMPONENT STREQUAL "Unspecified" OR NOT CMAKE_INSTALL_COMPONENT)
  if(EXISTS "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/codec2/codec2-config.cmake")
    file(DIFFERENT _cmake_export_file_changed FILES
         "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/codec2/codec2-config.cmake"
         "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/build/src/CMakeFiles/Export/5bd4d45600a6b952779acd0bfa505c3f/codec2-config.cmake")
    if(_cmake_export_file_changed)
      file(GLOB _cmake_old_config_files "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/codec2/codec2-config-*.cmake")
      if(_cmake_old_config_files)
        string(REPLACE ";" ", " _cmake_old_config_files_text "${_cmake_old_config_files}")
        message(STATUS "Old export file \"$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/codec2/codec2-config.cmake\" will be replaced.  Removing files [${_cmake_old_config_files_text}].")
        unset(_cmake_old_config_files_text)
        file(REMOVE ${_cmake_old_config_files})
      endif()
      unset(_cmake_old_config_files)
    endif()
    unset(_cmake_export_file_changed)
  endif()
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/codec2" TYPE FILE FILES "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/build/src/CMakeFiles/Export/5bd4d45600a6b952779acd0bfa505c3f/codec2-config.cmake")
  if(CMAKE_INSTALL_CONFIG_NAME MATCHES "^([Dd][Ee][Bb][Uu][Gg])$")
    file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/codec2" TYPE FILE FILES "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/build/src/CMakeFiles/Export/5bd4d45600a6b952779acd0bfa505c3f/codec2-config-debug.cmake")
  endif()
endif()

if(CMAKE_INSTALL_COMPONENT STREQUAL "lib" OR NOT CMAKE_INSTALL_COMPONENT)
  if(EXISTS "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libcodec2.so.1.2" AND
     NOT IS_SYMLINK "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libcodec2.so.1.2")
    file(RPATH_CHECK
         FILE "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libcodec2.so.1.2"
         RPATH "")
  endif()
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE SHARED_LIBRARY FILES "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/build/src/libcodec2.so.1.2")
  if(EXISTS "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libcodec2.so.1.2" AND
     NOT IS_SYMLINK "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libcodec2.so.1.2")
    if(CMAKE_INSTALL_DO_STRIP)
      execute_process(COMMAND "/usr/bin/strip" "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libcodec2.so.1.2")
    endif()
  endif()
endif()

if(CMAKE_INSTALL_COMPONENT STREQUAL "lib" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE SHARED_LIBRARY FILES "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/build/src/libcodec2.so")
endif()

if(CMAKE_INSTALL_COMPONENT STREQUAL "dev" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include/codec2" TYPE FILE FILES
    "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/src/codec2.h"
    "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/src/codec2_fdmdv.h"
    "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/src/codec2_cohpsk.h"
    "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/src/codec2_fm.h"
    "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/src/codec2_ofdm.h"
    "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/src/fsk.h"
    "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/src/codec2_fifo.h"
    "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/src/comp.h"
    "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/src/modem_stats.h"
    "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/src/freedv_api.h"
    "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/src/reliable_text.h"
    "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/src/codec2_math.h"
    "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/build/codec2/version.h"
    )
endif()

string(REPLACE ";" "\n" CMAKE_INSTALL_MANIFEST_CONTENT
       "${CMAKE_INSTALL_MANIFEST_FILES}")
if(CMAKE_INSTALL_LOCAL_ONLY)
  file(WRITE "/home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice/sigao_core/external/codec2/build/src/install_local_manifest.txt"
     "${CMAKE_INSTALL_MANIFEST_CONTENT}")
endif()
