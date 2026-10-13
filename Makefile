GRADLE_IMAGE ?= gradle:jdk25
ROOT_DIR := $(shell pwd)
DOCKER_ARGS = --rm -u $(shell id -u):$(shell id -g) -v $(ROOT_DIR):/app -v /tmp/.gradle-cache:/home/gradle/.gradle -w /app
DOCKER_RUN = docker run $(DOCKER_ARGS) $(GRADLE_IMAGE)
DOCKER_RUN_DEV = docker run $(DOCKER_ARGS) -p 25580:25580 -e APP_ENV=development -e LOBBY_BIND=0.0.0.0:25580 $(GRADLE_IMAGE)

.PHONY: all build run test check-comments check clean docker dev

all: build

build:
	$(DOCKER_RUN) gradle shadowJar --no-daemon

test:
	$(DOCKER_RUN) gradle test --no-daemon

check-comments:
	go run tools/nocomments/main.go .

check: check-comments test

run:
	$(DOCKER_RUN) gradle run --no-daemon

dev:
	$(DOCKER_RUN_DEV) gradle run --no-daemon

clean:
	$(DOCKER_RUN) gradle clean --no-daemon
	rm -rf build bin

docker:
	docker build -t oryza/lobby:latest .
