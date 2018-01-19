PROJECT(Aris C)
cmake_minimum_required(VERSION 2.8)

file(GLOB src "aris/*.c")

include_directories( include/ )
add_library( aris SHARED ${src})
