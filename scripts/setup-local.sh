#!/usr/bin/env sh
set -eu
repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
environment_path="$repository_root/.env"
if [ -f "$environment_path" ]; then
    printf '%s\n' 'O arquivo .env já existe e foi preservado.'
    exit 0
fi
umask 077
database_password=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
sed "s/^POSTGRES_PASSWORD=$/POSTGRES_PASSWORD=$database_password/" "$repository_root/.env.example" > "$environment_path"
printf '%s\n' 'Arquivo .env criado com senha local aleatória. Execute docker compose up --build -d --wait.'
