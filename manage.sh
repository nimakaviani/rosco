#!/bin/bash

set -ex

function build {
  version=${1:-latest}

  ./gradlew --no-daemon -PenableCrossCompilerPlugin=true rosco-web:installDist -x test

  docker build -t nimak/spinnaker-rosco:$version -f Dockerfile.slim .
}

function push {
  version=${1:-latest}

  docker push nimak/spinnaker-rosco:$version
}

function delete {
  kubectl delete pod -nspinnaker $(kubectl get pods -n spinnaker | grep rosco | awk '{print $1}')
}

case "$1" in
  build )
    build $2
    ;;

  push )
    push $2
    ;;

  delete )
    delete
    ;;

  * )
    build
    push
    delete
esac
