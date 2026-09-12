"""Compile exact locked AIR sources for FAST only; never claim upstream qualification.

The upstream Maven lifecycle deliberately forbids skip flags. FAST builds its
dependency with javac/jar and installs the original POMs, without changing those
POMs or entering upstream test/qualification lifecycle. LOCAL_QUALIFICATION uses
the original complete Maven lifecycle instead.
"""
from pathlib import Path
import shutil


def compile_dependency(checkout, build, execute, maven):
    outputs = []
    for module, artifact in (("air-model", "air-java"), ("air-json", "air-json")):
        sources = sorted((checkout / module / "src/main/java").rglob("*.java"))
        if not sources:
            raise RuntimeError("upstream fast compilation sources absent")
        classes = build / "fast-upstream" / module / "classes"
        if classes.exists():
            shutil.rmtree(classes)  # Task-owned output only; deleted upstream sources cannot leave stale classes.
        classes.mkdir(parents=True)
        args = ["javac", "--release", "21", "-Xlint:all", "-Werror", "-d", str(classes)]
        if outputs:
            args += ["-cp", str(outputs[0])]
        execute(args + [str(p) for p in sources])
        jar = classes.parent / (artifact + "-0.1.0-SNAPSHOT.jar")
        execute(["jar", "--create", "--file", str(jar), "--date=2026-09-05T00:00:00Z", "-C", str(classes), "."])
        outputs.append(jar)
    for pom, file in [(checkout / "pom.xml", checkout / "pom.xml"),
                      (checkout / "air-model/pom.xml", outputs[0]),
                      (checkout / "air-json/pom.xml", outputs[1])]:
        execute(maven("org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file",
                      "-Dfile=" + str(file), "-DpomFile=" + str(pom)), checkout)
    return "FAST_DEPENDENCY_COMPILE_ONLY: exact source javac --release 21; original POMs; upstream qualification NOT_RUN\n"
