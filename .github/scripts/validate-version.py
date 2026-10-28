#!/usr/bin/env python3
"""Valida a versao do pom (Maven) antes de construir a imagem. Falha (exit 1) se:

  1. o pom raiz nao declara <version> propria (herdada ou ausente) ou usa placeholder (${revision});
  2. a versao e -SNAPSHOT ou nao segue X.Y.Z;
  3. um modulo declara versao/parent diferente da raiz;
  4. a versao ja esta em uso: existe a tag git vX.Y.Z em OUTRO commit, ou a imagem IMAGE:X.Y.Z ja foi publicada.

Reexecutar o mesmo commit (re-run do CI) e permitido: a tag aponta para o proprio commit.

Variaveis: IMAGE (obrigatoria, ex.: ghcr.io/sebratel/api-gateway), GITHUB_SHA (padrao: HEAD),
CHECK_REGISTRY=false para pular a consulta ao registry (uso local), GITHUB_OUTPUT (recebe version=X.Y.Z).
Sem dependencias alem do Python 3, git e (opcional) docker.
"""
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
SEMVER = re.compile(r"^\d+\.\d+\.\d+$")
errors = []


def fail(msg):
    errors.append(msg)


def text(node, path):
    el = node.find(path, NS)
    return el.text.strip() if el is not None and el.text else None


def root_of(pom):
    return ET.parse(pom).getroot()


def run(cmd):
    return subprocess.run(cmd, capture_output=True, text=True)


def main():
    image = os.environ.get("IMAGE", "").strip()
    if not image:
        sys.exit("Defina IMAGE (ex.: ghcr.io/sebratel/api-gateway).")
    sha = os.environ.get("GITHUB_SHA") or run(["git", "rev-parse", "HEAD"]).stdout.strip()
    check_registry = os.environ.get("CHECK_REGISTRY", "true").lower() != "false"

    root = root_of("pom.xml")
    version = text(root, "m:version")
    if not version:
        fail("pom.xml: o projeto nao declara <version> propria (nao basta herdar do parent). Declare uma versao X.Y.Z.")
    elif "${" in version:
        fail(f"pom.xml: a versao '{version}' usa placeholder; declare a versao literal X.Y.Z.")
    elif version.upper().endswith("-SNAPSHOT"):
        fail(f"pom.xml: a versao '{version}' e SNAPSHOT. Imagens so saem de versoes de release X.Y.Z.")
    elif not SEMVER.match(version):
        fail(f"pom.xml: a versao '{version}' nao segue o formato X.Y.Z.")

    # Modulos: versao propria (se houver) e a do parent precisam bater com a raiz.
    if version:
        artifact = text(root, "m:artifactId")
        for module in [m.text.strip() for m in root.findall("m:modules/m:module", NS) if m.text]:
            pom = os.path.join(module, "pom.xml")
            if not os.path.exists(pom):
                continue
            node = root_of(pom)
            own = text(node, "m:version")
            parent_version = text(node, "m:parent/m:version")
            parent_artifact = text(node, "m:parent/m:artifactId")
            if own and own != version:
                fail(f"{pom}: versao '{own}' diferente da raiz '{version}'.")
            if parent_artifact == artifact and parent_version != version:
                fail(f"{pom}: <parent> aponta para '{parent_version}', mas a raiz esta em '{version}'.")

    if version and not errors:
        tag = f"v{version}"
        out = run(["git", "ls-remote", "--tags", "origin", f"refs/tags/{tag}", f"refs/tags/{tag}^{{}}"]).stdout.split()
        # linhas "sha ref"; para tag anotada vale o sha de ^{} (commit apontado)
        shas = {out[i + 1]: out[i] for i in range(0, len(out) - 1, 2)}
        tagged = shas.get(f"refs/tags/{tag}^{{}}") or shas.get(f"refs/tags/{tag}")
        if tagged and tagged != sha:
            fail(f"a versao {version} ja esta em uso: a tag {tag} aponta para {tagged[:7]}, nao para este commit ({sha[:7]}). "
                 "Aumente a versao no pom.")
        elif not tagged and check_registry:
            probe = run(["docker", "manifest", "inspect", f"{image}:{version}"])
            if probe.returncode == 0:
                fail(f"a versao {version} ja esta em uso: a imagem {image}:{version} ja foi publicada. Aumente a versao no pom.")

    if errors:
        for e in errors:
            print(f"::error::{e}")
        sys.exit(1)

    print(f"OK: versao {version} valida e livre para {image}")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as f:
            f.write(f"version={version}\n")


if __name__ == "__main__":
    main()
