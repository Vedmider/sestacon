#!/bin/bash
# Local build environment: Temurin JDK 21 + local Maven 3.9.9
export JAVA_HOME="$HOME/tools/jdk-21.0.13+9"
export PATH="$JAVA_HOME/bin:$HOME/tools/apache-maven-3.9.9/bin:$PATH"
# DJL PyTorch native: this el8 host's libstdc++ lacks GLIBCXX_3.4.26, so use the
# pre-cxx11 libtorch build (ships its own libstdc++).
export PYTORCH_PRECXX11=true
