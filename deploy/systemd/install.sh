#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: $0 orchestrator|worker|all"
    echo "Build and install systemd user services; does not start or enable them."
}

case "${1:-}" in
    orchestrator|worker) roles=("$1") ;;
    all) roles=(orchestrator worker) ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
esac
if (( $# != 1 )); then
    usage >&2
    exit 2
fi

source_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(cd -- "$source_dir/../.." && pwd)
config_home=${XDG_CONFIG_HOME:-"$HOME/.config"}
config_dir="$config_home/dewy"
unit_dir="$config_home/systemd/user"
bin_dir="$HOME/.local/lib/dewy"

for dependency in akiko systemctl install mktemp; do
    if ! command -v "$dependency" >/dev/null; then
        echo "Missing dependency: $dependency" >&2
        exit 1
    fi
done
if [[ " ${roles[*]} " == *" worker "* ]] && ! command -v tmux >/dev/null; then
    echo "Missing dependency: tmux (required by the worker)" >&2
    exit 1
fi
if [[ "$config_home" != /* ]]; then
    echo "XDG_CONFIG_HOME must be an absolute path" >&2
    exit 1
fi

# Build everything before replacing an installed binary.
for role in "${roles[@]}"; do
    (cd -- "$repo_dir" && akiko build "$role")
done

install -d -m 700 "$config_dir"
install -d "$unit_dir" "$bin_dir"
temporary=
trap 'if [[ -n "$temporary" ]]; then rm -f -- "$temporary"; fi' EXIT
for role in "${roles[@]}"; do
    # Rename rather than overwrite: live workers and tmux supervisors may
    # still be executing the previous binary during an upgrade.
    temporary=$(mktemp "$bin_dir/.$role.XXXXXX")
    install -m 755 "$repo_dir/$role/build/$role" "$temporary"
    mv -f -- "$temporary" "$bin_dir/$role"
    temporary=
    install -m 644 "$source_dir/dewy-$role.service" "$unit_dir/dewy-$role.service"

    if [[ ! -e "$config_dir/$role.env" ]]; then
        # Keep first-time configuration private even with a permissive umask.
        temporary=$(mktemp "$config_dir/.$role.env.XXXXXX")
        install -m 600 "$source_dir/$role.env.example" "$temporary"
        if [[ "$role" == worker ]]; then
            # Quote for systemd's EnvironmentFile syntax, not shell evaluation.
            service_path=${PATH//\\/\\\\}
            service_path=${service_path//\"/\\\"}
            service_path=${service_path//\$/\\\$}
            service_path=${service_path//\`/\\\`}
            printf '\nPATH="%s"\n' "$service_path" >> "$temporary"
        fi
        mv -- "$temporary" "$config_dir/$role.env"
        temporary=
    fi
    printf 'Installed dewy-%s.service; configuration: %s/%s.env\n' "$role" "$config_dir" "$role"
done

systemctl --user daemon-reload
echo "Edit the configuration files, then enable and start the installed services."
