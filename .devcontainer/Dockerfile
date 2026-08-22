FROM ubuntu:24.04

RUN apt-get update && apt-get install -y \
    git \
    curl \
    ca-certificates \
    unzip \
    zip \
    sudo \
    openssh-client \
    openjdk-21-jdk \
    maven \
	bubblewrap \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -m -s /bin/bash developer \
    && echo "developer ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/developer

USER developer

WORKDIR /workspace

ENV PATH="/home/developer/.local/bin:${PATH}"

RUN curl -fsSL https://chatgpt.com/codex/install.sh | sh

CMD ["sleep", "infinity"]