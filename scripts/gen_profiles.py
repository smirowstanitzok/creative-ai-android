#!/usr/bin/env python3
"""Erzeugt profiles.json für die Android-App aus der Python-Referenz.

Die Docker-Overrides (dockerStartCmd / dockerEntrypoint) werden 1:1 aus
main.build_start_override() übernommen, damit der gestartete Pod byte-genau so
provisioniert wird wie vom Python-Skript.
"""
import json
import os
import sys

# Nachbar-Repo `creative-ai` (relativ zu diesem Skript, überschreibbar per Env).
_here = os.path.dirname(os.path.abspath(__file__))
_default = os.path.normpath(os.path.join(_here, "..", "..", "creative-ai"))
sys.path.insert(0, os.environ.get("CREATIVE_AI_DIR", _default))
import main  # noqa: E402

out = []
for p in main.PROFILES:
    override = main.build_start_override(p)  # {} | {dockerStartCmd} | {dockerEntrypoint, dockerStartCmd}
    out.append(
        {
            "key": p.key,
            "name": p.name,
            "provides": p.provides,
            "hint": p.hint,
            "image": p.image,
            "primaryPort": p.primary_port,
            "extraPorts": list(p.extra_ports),
            "httpPorts": list(p.http_ports),
            "diskGb": p.disk_gb,
            "gpuTypeIds": list(p.gpu_type_ids),
            "cudaVersions": list(p.cuda_versions),
            "jupyterEnv": p.jupyter_env,
            "needsJupyterCred": main.JUPYTER_PORT in p.http_ports,
            "needsFilebrowserCred": main.FILEBROWSER_PORT in p.http_ports,
            "dockerOverride": override,
        }
    )

print(json.dumps(out, indent=2, ensure_ascii=False))
