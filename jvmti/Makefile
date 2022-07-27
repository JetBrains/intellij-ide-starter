# JVM TI agent that logs loading and preparation stages of class lifecycle
# Usage VM Options: -agentpath:/var/tmp/libvmtrace.so=output.log
# Original source: https://github.com/odnoklassniki/jvmti-tools#vmtrace

all: build

build:
	g++ -O2 -fPIC -shared -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/linux -olibvmtrace.so vmtrace.cpp

build-mac-intel:
  g++ -O2 -fPIC -shared -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/darwin -olibvmtrace.dylib vmtrace.cpp

clean:
	rm libvmtrace.so

default: all