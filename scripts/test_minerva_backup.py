import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from unittest.mock import call, patch

import minerva_backup


class MinervaBackupTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def create_valid_backup(self):
        data_root = self.root / "source"
        (data_root / "memories").mkdir(parents=True, exist_ok=True)
        (data_root / "memories" / "memory.md").write_text("memory", encoding="utf-8")
        database_dump = self.root / "source.dump"
        database_dump.write_bytes(b"postgres dump")
        archive_path = self.root / "restore.zip"
        minerva_backup.create_archive(database_dump, data_root, archive_path)
        return archive_path

    def test_archive_contains_user_data_and_excludes_runtime_files(self):
        data_root = self.root / "source"
        (data_root / "skills" / "example").mkdir(parents=True)
        (data_root / "memories").mkdir()
        (data_root / "personality.md").write_text("preferences", encoding="utf-8")
        (data_root / "heartbeat.md").write_text("Check recent events", encoding="utf-8")
        (data_root / "skills" / "example" / "SKILL.md").write_text("skill", encoding="utf-8")
        (data_root / "memories" / "MEMORY.md").write_text("memory", encoding="utf-8")
        (data_root / "logs").mkdir()
        (data_root / "logs" / "application.log").write_text("log", encoding="utf-8")
        (data_root / "uploads").mkdir()
        (data_root / "uploads" / "temporary.bin").write_bytes(b"temporary")
        database_dump = self.root / "database.dump"
        database_dump.write_bytes(b"postgres dump")
        archive_path = self.root / "backup.zip"

        minerva_backup.create_archive(database_dump, data_root, archive_path)

        with zipfile.ZipFile(archive_path) as archive:
            self.assertEqual(
                {"database.dump", "manifest.json", "data/personality.md",
                 "data/heartbeat.md",
                 "data/skills/example/SKILL.md", "data/memories/MEMORY.md"},
                set(archive.namelist()),
            )

        database, restored_data = minerva_backup.validate_and_extract(archive_path, self.root / "extracted")
        self.assertEqual(database_dump.read_bytes(), database.read_bytes())
        self.assertEqual("preferences", (restored_data / "personality.md").read_text(encoding="utf-8"))
        self.assertEqual(
            "Check recent events",
            (restored_data / "heartbeat.md").read_text(encoding="utf-8"),
        )
        self.assertEqual("memory", (restored_data / "memories" / "MEMORY.md").read_text(encoding="utf-8"))

    def test_rejects_traversal_paths_before_extracting_files(self):
        archive_path = self.root / "unsafe.zip"
        database = b"database"
        unsafe_name = "data/skills/../../outside.txt"
        manifest = {
            "format_version": minerva_backup.BACKUP_VERSION,
            "database_sha256": hashlib.sha256(database).hexdigest(),
            "files": {unsafe_name: hashlib.sha256(b"unsafe").hexdigest()},
        }
        with zipfile.ZipFile(archive_path, "w") as archive:
            archive.writestr("database.dump", database)
            archive.writestr("manifest.json", json.dumps(manifest))
            archive.writestr(unsafe_name, b"unsafe")

        with self.assertRaises(minerva_backup.BackupError):
            minerva_backup.validate_and_extract(archive_path, self.root / "extracted")

        self.assertFalse((self.root / "outside.txt").exists())

    def test_rejects_incompatible_format_and_checksum_mismatch(self):
        database_dump = self.root / "database.dump"
        database_dump.write_bytes(b"database")
        data_root = self.root / "data"
        (data_root / "memories").mkdir(parents=True)
        (data_root / "memories" / "memory.md").write_text("memory", encoding="utf-8")
        archive_path = self.root / "backup.zip"
        minerva_backup.create_archive(database_dump, data_root, archive_path)

        with zipfile.ZipFile(archive_path) as source:
            entries = {name: source.read(name) for name in source.namelist()}
        manifest = json.loads(entries["manifest.json"])
        manifest["format_version"] = minerva_backup.BACKUP_VERSION + 1
        entries["manifest.json"] = json.dumps(manifest).encode()
        incompatible = self.root / "incompatible.zip"
        with zipfile.ZipFile(incompatible, "w") as archive:
            for name, content in entries.items():
                archive.writestr(name, content)
        with self.assertRaisesRegex(minerva_backup.BackupError, "Unsupported backup format"):
            minerva_backup.validate_and_extract(incompatible, self.root / "incompatible-extracted")

        manifest["format_version"] = minerva_backup.BACKUP_VERSION
        entries["manifest.json"] = json.dumps(manifest).encode()
        entries["database.dump"] = b"changed"
        corrupted = self.root / "corrupted.zip"
        with zipfile.ZipFile(corrupted, "w") as archive:
            for name, content in entries.items():
                archive.writestr(name, content)
        with self.assertRaisesRegex(minerva_backup.BackupError, "database dump checksum"):
            minerva_backup.validate_and_extract(corrupted, self.root / "corrupted-extracted")

    def test_restore_requires_explicit_replace_confirmation(self):
        with self.assertRaisesRegex(minerva_backup.BackupError, "Pass --replace"):
            minerva_backup.restore([], self.root / "backup.zip", replace=False)

    def test_backup_does_not_overwrite_an_existing_file_by_default(self):
        destination = self.root / "existing.zip"
        destination.write_bytes(b"keep")

        with self.assertRaisesRegex(minerva_backup.BackupError, "pass --force"):
            minerva_backup.backup([], destination)

        self.assertEqual(b"keep", destination.read_bytes())

    @patch("minerva_backup._restore_files")
    @patch("minerva_backup._restore_database")
    @patch("minerva_backup._database_dump")
    @patch("minerva_backup._verify_database_dump")
    @patch("minerva_backup._compose")
    @patch("minerva_backup._require_running")
    def test_restore_replaces_database_and_files_then_restarts_core(
        self, require_running, compose, verify_dump, dump_database, restore_database, restore_files
    ):
        archive_path = self.create_valid_backup()
        dump_database.side_effect = lambda _files, path: Path(path).write_bytes(b"previous database")

        result = minerva_backup.restore([], archive_path, replace=True)

        self.assertEqual(archive_path, result)
        self.assertEqual(2, require_running.call_count)
        verify_dump.assert_called_once()
        self.assertEqual(1, dump_database.call_count)
        self.assertEqual(1, restore_database.call_count)
        self.assertEqual(["apply", "commit"], [entry.args[3] for entry in restore_files.call_args_list])
        self.assertEqual([call([], "stop", "core"), call([], "start", "core")], compose.call_args_list)

    @patch("minerva_backup._restore_files")
    @patch("minerva_backup._restore_database")
    @patch("minerva_backup._database_dump")
    @patch("minerva_backup._verify_database_dump")
    @patch("minerva_backup._compose")
    @patch("minerva_backup._require_running")
    def test_restore_rolls_back_database_and_restarts_core_on_failure(
        self, require_running, compose, verify_dump, dump_database, restore_database, restore_files
    ):
        archive_path = self.create_valid_backup()
        dump_database.side_effect = lambda _files, path: Path(path).write_bytes(b"previous database")
        restore_database.side_effect = [minerva_backup.BackupError("restore failed"), None]

        with self.assertRaisesRegex(minerva_backup.BackupError, "restore failed"):
            minerva_backup.restore([], archive_path, replace=True)

        self.assertEqual(2, restore_database.call_count)
        restore_files.assert_not_called()
        self.assertEqual([call([], "stop", "core"), call([], "start", "core")], compose.call_args_list)

    @patch("minerva_backup._restore_files")
    @patch("minerva_backup._restore_database")
    @patch("minerva_backup._database_dump")
    @patch("minerva_backup._verify_database_dump")
    @patch("minerva_backup._compose")
    @patch("minerva_backup._require_running")
    def test_restore_rolls_back_files_and_database_if_file_install_fails(
        self, require_running, compose, verify_dump, dump_database, restore_database, restore_files
    ):
        archive_path = self.create_valid_backup()
        dump_database.side_effect = lambda _files, path: Path(path).write_bytes(b"previous database")
        restore_files.side_effect = [minerva_backup.BackupError("file install failed"), None]

        with self.assertRaisesRegex(minerva_backup.BackupError, "file install failed"):
            minerva_backup.restore([], archive_path, replace=True)

        self.assertEqual(2, restore_database.call_count)
        self.assertEqual(["apply", "rollback"], [entry.args[3] for entry in restore_files.call_args_list])
        self.assertEqual([call([], "stop", "core"), call([], "start", "core")], compose.call_args_list)


if __name__ == "__main__":
    unittest.main()
