#!/usr/bin/env python3
"""Create and restore local Minerva backups for Docker Compose deployments."""

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
import tempfile
import uuid
import zipfile
import zlib


ROOT = Path(__file__).resolve().parents[1]
CORE_SERVICE = "core"
DB_SERVICE = "db"
DATA_ROOT = "/MinervaOS"
BACKUP_VERSION = 1
MANIFEST_NAME = "manifest.json"
DATABASE_NAME = "database.dump"
DATA_PREFIX = "data/"
ALLOWED_DATA_ROOTS = ("skills", "memories", "personality.md")
CHUNK_SIZE = 1024 * 1024


class BackupError(RuntimeError):
    pass


def _compose_base(compose_files):
    command = ["docker", "compose"]
    for compose_file in compose_files:
        command.extend(["-f", str(compose_file)])
    return command


def _run(command, *, input_data=None, stdout=None, check=True):
    result = subprocess.run(
        command,
        cwd=ROOT,
        input=input_data,
        stdout=subprocess.PIPE if stdout is None else stdout,
        stderr=subprocess.PIPE,
        check=False,
    )
    if check and result.returncode:
        message = result.stderr.decode(errors="replace").strip()
        raise BackupError(f"Command failed ({result.returncode}): {' '.join(command)}\n{message}")
    return result


def _compose(compose_files, *args, input_data=None, stdout=None, check=True):
    return _run(_compose_base(compose_files) + list(args), input_data=input_data, stdout=stdout, check=check)


def _compose_stream(compose_files, args, input_path, *, stdout=None):
    command = _compose_base(compose_files) + list(args)
    with tempfile.TemporaryFile() as errors, open(input_path, "rb") as source:
        process = subprocess.Popen(
            command,
            cwd=ROOT,
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL if stdout is None else stdout,
            stderr=errors,
        )
        try:
            shutil.copyfileobj(source, process.stdin, CHUNK_SIZE)
            process.stdin.close()
            return_code = process.wait()
        except BaseException:
            process.kill()
            process.wait()
            raise
        errors.seek(0)
        error_text = errors.read().decode(errors="replace").strip()
    if return_code:
        raise BackupError(f"Command failed ({return_code}): {' '.join(command)}\n{error_text}")


def _require_running(compose_files, service):
    result = _compose(compose_files, "ps", "--status", "running", "-q", service)
    if not result.stdout.strip():
        raise BackupError(f"The Compose service '{service}' must be running.")


def _sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(CHUNK_SIZE), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _safe_data_path(name):
    path = PurePosixPath(name)
    if str(path) != name or path.is_absolute() or "\\" in name or any(part in ("", ".", "..") for part in path.parts):
        return False
    return any(name == root or name.startswith(root + "/") for root in ALLOWED_DATA_ROOTS)


def create_archive(database_dump, data_root, archive_path):
    data_root = Path(data_root)
    archive_path = Path(archive_path)
    files = {}
    source_files = []
    for root_name in ALLOWED_DATA_ROOTS:
        source_root = data_root / root_name
        if not source_root.exists() and not source_root.is_symlink():
            continue
        if source_root.is_symlink():
            raise BackupError(f"Symbolic links are not allowed in backup data: {source_root}")
        if source_root.is_file():
            candidates = [source_root]
        else:
            candidates = sorted(source_root.rglob("*"))
        for source in candidates:
            if source.is_symlink():
                raise BackupError(f"Symbolic links are not allowed in backup data: {source}")
            if source.is_file():
                name = (Path(DATA_PREFIX) / source.relative_to(data_root)).as_posix()
                files[name] = _sha256_file(source)
                source_files.append((source, name))
            elif not source.is_dir():
                raise BackupError(f"Unsupported filesystem entry in backup data: {source}")

    manifest = {
        "format_version": BACKUP_VERSION,
        "database_sha256": _sha256_file(database_dump),
        "files": files,
    }
    archive_path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{archive_path.name}.", suffix=".tmp",
                                                   dir=archive_path.parent)
    os.close(descriptor)
    temporary_path = Path(temporary_name)
    try:
        with zipfile.ZipFile(temporary_path, "w") as archive:
            archive.write(database_dump, DATABASE_NAME, compress_type=zipfile.ZIP_STORED)
            for source, name in source_files:
                archive.write(source, name, compress_type=zipfile.ZIP_DEFLATED)
            archive.writestr(MANIFEST_NAME, json.dumps(manifest, indent=2, sort_keys=True))
        os.replace(temporary_path, archive_path)
    finally:
        temporary_path.unlink(missing_ok=True)


def validate_and_extract(archive_path, destination):
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=False)
    try:
        with zipfile.ZipFile(archive_path) as archive:
            infos = archive.infolist()
            names = [info.filename for info in infos]
            if len(names) != len(set(names)):
                raise BackupError("The backup contains duplicate archive entries.")
            if MANIFEST_NAME not in names or DATABASE_NAME not in names:
                raise BackupError("The backup is missing its manifest or database dump.")

            info_by_name = {info.filename: info for info in infos}
            manifest = json.loads(archive.read(MANIFEST_NAME))
            if not isinstance(manifest, dict):
                raise BackupError("The backup manifest must be a JSON object.")
            if manifest.get("format_version") != BACKUP_VERSION:
                raise BackupError(f"Unsupported backup format version: {manifest.get('format_version')!r}")
            expected_files = manifest.get("files")
            if not isinstance(expected_files, dict):
                raise BackupError("The backup manifest has an invalid files list.")
            if any(not isinstance(name, str) or not isinstance(digest, str)
                   or len(digest) != 64
                   or any(character not in "0123456789abcdef" for character in digest)
                   or not name.startswith(DATA_PREFIX)
                   or not _safe_data_path(name.removeprefix(DATA_PREFIX))
                   for name, digest in expected_files.items()):
                raise BackupError("The backup manifest contains an invalid data path or checksum.")
            archive_data_names = {name for name in names if name.startswith(DATA_PREFIX)}
            if archive_data_names != set(expected_files):
                raise BackupError("The backup contents do not match the manifest.")
            if set(names) != {MANIFEST_NAME, DATABASE_NAME} | archive_data_names:
                raise BackupError("The backup contains unexpected entries.")
            for info in infos:
                mode = info.external_attr >> 16
                if (mode & 0o170000) == 0o120000:
                    raise BackupError(f"Symbolic links are not allowed in backups: {info.filename}")
            database_info = info_by_name[DATABASE_NAME]
            database_checksum = manifest.get("database_sha256")
            if (not isinstance(database_checksum, str) or len(database_checksum) != 64
                    or any(character not in "0123456789abcdef" for character in database_checksum)):
                raise BackupError("The backup manifest has an invalid database checksum.")

            database_path = destination / DATABASE_NAME
            with archive.open(database_info) as source, open(database_path, "wb") as target:
                digest = hashlib.sha256()
                for chunk in iter(lambda: source.read(CHUNK_SIZE), b""):
                    digest.update(chunk)
                    target.write(chunk)
            if digest.hexdigest() != database_checksum:
                raise BackupError("The database dump checksum does not match the manifest.")

            data_root = destination / "data"
            for name, expected_digest in expected_files.items():
                relative_name = name[len(DATA_PREFIX):]
                output_path = data_root.joinpath(*PurePosixPath(relative_name).parts)
                output_path.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(info_by_name[name]) as source, open(output_path, "wb") as target:
                    digest = hashlib.sha256()
                    for chunk in iter(lambda: source.read(CHUNK_SIZE), b""):
                        digest.update(chunk)
                        target.write(chunk)
                if digest.hexdigest() != expected_digest:
                    raise BackupError(f"Checksum mismatch for backup file: {name}")
        return database_path, data_root
    except BackupError:
        shutil.rmtree(destination, ignore_errors=True)
        raise
    except (OSError, zipfile.BadZipFile, ValueError, KeyError, TypeError, RuntimeError, EOFError, zlib.error) as error:
        shutil.rmtree(destination, ignore_errors=True)
        raise BackupError(f"Could not read backup archive: {error}") from error


def _database_dump(compose_files, output_path):
    with open(output_path, "wb") as output:
        _compose(compose_files, "exec", "-T", DB_SERVICE, "pg_dump", "-U", "postgres",
                 "--format=custom", "--dbname=postgres", stdout=output)


def _restore_database(compose_files, dump_path):
    _compose_stream(compose_files,
                    ["exec", "-T", DB_SERVICE, "pg_restore", "--clean", "--if-exists",
                     "--no-owner", "--no-acl", "--exit-on-error", "--dbname=postgres"],
                    dump_path)


def _verify_database_dump(compose_files, dump_path):
    _compose_stream(compose_files, ["exec", "-T", DB_SERVICE, "pg_restore", "--list", "-"], dump_path,
                    stdout=subprocess.DEVNULL)


def _restore_files(compose_files, data_root, operation_id, action):
    bind_path = str(Path(data_root).resolve())
    if os.name == "nt":
        bind_path = bind_path.replace("\\", "/")
    stage_name = f".minerva-restore-stage-{operation_id}"
    previous_name = f".minerva-restore-previous-{operation_id}"
    if action == "apply":
        command = f"""
set -eu
root={DATA_ROOT}
stage="$root/{stage_name}"
previous="$root/{previous_name}"
mkdir -p "$stage" "$previous"
cp -a /minerva-restore-input/. "$stage/"
for name in skills memories personality.md; do
  if [ -e "$stage/$name" ]; then touch "$previous/.new-$name"; fi
  if [ -e "$root/$name" ]; then touch "$previous/.had-$name"; fi
done
for name in skills memories personality.md; do
  if [ -e "$root/$name" ]; then mv "$root/$name" "$previous/$name"; fi
done
for name in skills memories personality.md; do
  if [ -e "$stage/$name" ]; then mv "$stage/$name" "$root/$name"; fi
done
for name in skills memories personality.md; do
  if [ -e "$root/$name" ]; then chown -R 10001:10001 "$root/$name"; fi
done
"""
    elif action == "rollback":
        command = f"""
set -eu
root={DATA_ROOT}
previous="$root/{previous_name}"
for name in skills memories personality.md; do
  if [ -e "$previous/.had-$name" ]; then
    if [ -e "$previous/$name" ]; then
      rm -rf "$root/$name"
      mv "$previous/$name" "$root/$name"
    fi
  elif [ -e "$previous/.new-$name" ]; then
    rm -rf "$root/$name"
  fi
done
rm -rf "$root/{stage_name}" "$previous"
"""
    elif action == "commit":
        command = f"rm -rf {DATA_ROOT}/{stage_name} {DATA_ROOT}/{previous_name}"
    else:
        raise ValueError(f"Unknown restore action: {action}")

    arguments = [
        "run", "--rm", "--no-deps", "-T", "--user", "0:0",
        "--volume", f"{bind_path}:/minerva-restore-input:ro",
        "--entrypoint", "sh", CORE_SERVICE, "-c", command,
    ]
    _compose(compose_files, *arguments)


def backup(compose_files, output_path, force=False):
    output_path = Path(output_path).expanduser().resolve()
    if output_path.exists() and not force:
        raise BackupError(f"Backup already exists; pass --force to replace it: {output_path}")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    _require_running(compose_files, CORE_SERVICE)
    _require_running(compose_files, DB_SERVICE)
    stopped = False
    try:
        _compose(compose_files, "stop", CORE_SERVICE)
        stopped = True
        with tempfile.TemporaryDirectory(prefix="minerva-backup-") as temporary:
            temporary = Path(temporary)
            data_root = temporary / "data-root"
            _compose(compose_files, "cp", f"{CORE_SERVICE}:{DATA_ROOT}", str(data_root))
            database_dump = temporary / DATABASE_NAME
            _database_dump(compose_files, database_dump)

            descriptor, temporary_name = tempfile.mkstemp(
                prefix=f".{output_path.name}.", suffix=".tmp", dir=output_path.parent
            )
            os.close(descriptor)
            temporary_archive = Path(temporary_name)
            try:
                create_archive(database_dump, data_root, temporary_archive)
                if output_path.exists() and not force:
                    raise BackupError(f"Backup already exists; pass --force to replace it: {output_path}")
                os.replace(temporary_archive, output_path)
            finally:
                temporary_archive.unlink(missing_ok=True)
    finally:
        if stopped:
            _compose(compose_files, "start", CORE_SERVICE)
    return output_path


def restore(compose_files, archive_path, replace):
    if not replace:
        raise BackupError("Restore replaces conversations and user data. Pass --replace to confirm.")
    _require_running(compose_files, CORE_SERVICE)
    _require_running(compose_files, DB_SERVICE)
    operation_id = uuid.uuid4().hex
    stopped = False
    database_may_be_changed = False
    files_may_be_changed = False
    with tempfile.TemporaryDirectory(prefix="minerva-restore-") as temporary:
        temporary = Path(temporary)
        database_path, data_root = validate_and_extract(archive_path, temporary / "validated")
        _verify_database_dump(compose_files, database_path)
        rollback_database = temporary / "rollback.dump"
        try:
            _compose(compose_files, "stop", CORE_SERVICE)
            stopped = True
            _database_dump(compose_files, rollback_database)
            database_may_be_changed = True
            _restore_database(compose_files, database_path)
            files_may_be_changed = True
            _restore_files(compose_files, data_root, operation_id, "apply")
        except Exception as error:
            rollback_errors = []
            if files_may_be_changed:
                try:
                    _restore_files(compose_files, data_root, operation_id, "rollback")
                except Exception as rollback_error:
                    rollback_errors.append(f"file rollback failed: {rollback_error}")
            if database_may_be_changed and rollback_database.exists():
                try:
                    _restore_database(compose_files, rollback_database)
                except Exception as rollback_error:
                    rollback_errors.append(f"database rollback failed: {rollback_error}")
            if rollback_errors:
                raise BackupError(f"Restore failed: {error}; {'; '.join(rollback_errors)}") from error
            raise
        else:
            _restore_files(compose_files, data_root, operation_id, "commit")
        finally:
            if stopped:
                _compose(compose_files, "start", CORE_SERVICE)
    return Path(archive_path)


def _parser():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-f", "--compose-file", action="append", default=[],
                        help="Compose file (may be specified more than once; defaults to compose.yaml).")
    subparsers = parser.add_subparsers(dest="action", required=True)
    backup_parser = subparsers.add_parser("backup", help="Create a local backup archive.")
    backup_parser.add_argument("--output", required=True, type=Path, help="Path for the new backup archive.")
    backup_parser.add_argument("--force", action="store_true", help="Replace an existing backup file.")
    restore_parser = subparsers.add_parser("restore", help="Restore a local backup archive.")
    restore_parser.add_argument("--archive", required=True, type=Path, help="Backup archive to restore.")
    restore_parser.add_argument("--replace", action="store_true",
                                help="Confirm replacement of the current conversations and user data.")
    return parser


def main(argv=None):
    args = _parser().parse_args(argv)
    compose_files = args.compose_file or ["compose.yaml"]
    try:
        if args.action == "backup":
            result = backup(compose_files, args.output, args.force)
            print(f"Backup created: {result}")
        else:
            result = restore(compose_files, args.archive.expanduser().resolve(), args.replace)
            print(f"Backup restored: {result}")
        return 0
    except (BackupError, OSError) as error:
        print(f"Minerva backup error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
