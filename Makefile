CC = gcc # C compiler
LDFLAGS = -shared -lpam
RM = rm -f  # rm command
TARGET_LIB = assign-server/src/main/resources/libassign_pam.so # target lib

INC = $(JAVA_HOME)/include $(JAVA_HOME)/include/linux ./include
INCDIRS = $(INC:%=-I%)

CFLAGS = ${INCDIRS} -Wall -Wextra -fPIC -O2 -g

SRCS = src/assign_pam_auth.c # source files
OBJS = $(SRCS:.c=.o)

.PHONY: all
all: ${TARGET_LIB}

$(TARGET_LIB): $(OBJS) include/edu_rpi_aris_assign_server_auth_PAMLoginAuth.h
	$(CC) ${LDFLAGS} -o $@ $(OBJS)

include/edu_rpi_aris_assign_server_auth_PAMLoginAuth.h: gradle
	cd assign-server/build/classes/java/main; javah -classpath ../../../../../assign-server/jars/\* -jni -v -d ../../../../../include edu.rpi.aris.assign.server.auth.PAMLoginAuth

gradle:
	./gradlew build

%.o: %.c include/edu_rpi_aris_assign_server_auth_PAMLoginAuth.h
	$(CC) -c $(CPPFLAGS) $(CFLAGS) -o $@ $<

.PHONY: clean
clean:
	-${RM} ${TARGET_LIB} ${OBJS} include/edu_rpi_aris_assign_server_auth_PAMLoginAuth.h
