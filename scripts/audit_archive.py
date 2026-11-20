#!/usr/bin/env python3
"""Pack, verify, open and restore monthly audit-log archives (US-03).

A closed month of the audit log is packed into ONE file, audit_log_YYYY-MM.zip:

  manifest.json                    rows, id range, columns, SHA-256 of every block and of the content
  SSSS/CC.xz                       one xz block per segment (32 768 rows) and column

The rows are read from PostgreSQL in COPY text format and stored column by
column: values of one column (sequential ids, timestamps, the same action
codes ...) sit next to each other, so LZMA compresses them far better and much
faster than row-wise CSV. All blocks are compressed and decompressed in
parallel threads. Next to the archive, audit_log_YYYY-MM.zip.sha256 allows a
quick integrity check (sha256sum format).

Measured with 100 000 events (22 MB as CSV), 32 cores, PostgreSQL 17.12:
  previous format (row CSV, xz -9e, one stream): pack 34.5 s, 373 KB, open 0.71 s
  this format (columnar, xz -5, parallel):       pack ~1.2 s, 178 KB, open ~0.35 s

Only the Python standard library is used. Database access goes through psql;
by default inside the compose stack:

    docker compose exec -T db psql -U hms -d hms_community

Override with --psql "<command>" or the HMS_PSQL environment variable.

Commands
    list                         live partitions, archives and their sizes
    export  --month 2026-10      pack a closed month into DIR/audit_log_2026-10.zip
    verify  FILE [--check-db]    check every block, the content hash and (optionally) the DB record
    open    FILE [--output CSV]  write a CSV (UTF-8 with BOM, opens in Excel)
    drop    --month 2026-10 --yes   remove an archived month from the live DB (file must verify first)
    restore FILE                 load a dropped month back for searching (audit_search)
    unload  --month 2026-10      remove a restored copy again (archive file untouched)
"""

from __future__ import annotations

import argparse
import concurrent.futures as cf
import datetime as dt
import hashlib
import json
import lzma
import os
import re
import shlex
import subprocess
import sys
import time
import zipfile
from pathlib import Path

DEFAULT_PSQL = "docker compose exec -T db psql -U hms -d hms_community"
PSQL_FLAGS = ["-X", "-q", "-v", "ON_ERROR_STOP=1"]
SET_UTF8 = "SET client_encoding = 'UTF8'"
FORMAT = "hms-audit-columnar-v1"
COMPRESSION = "xz5-columnar"
PRESET = 5            # measured: as small as presets 6-9 here and the fastest
SEGMENT_ROWS = 32768  # enough blocks to keep all cores busy, ~4 % larger than 65 536
CHUNK = 1 << 20
NULL = b"\\N"
CSV_COLUMNS = [
    "id", "occurred_at_utc", "actor_user_id", "actor_username", "actor_role", "action",
    "action_category", "result", "entity_type", "entity_id", "session_id", "details",
]
ESCAPES = {"\\\\": "\\", "\\n": "\n", "\\r": "\r", "\\t": "\t", "\\b": "\b", "\\f": "\f", "\\v": "\v"}
ESCAPE_RE = re.compile(r"\\[\\nrtbfv]")


class ArchiveError(Exception):
    pass


# --------------------------------------------------------------------------- psql
def psql_base(args) -> list[str]:
    return shlex.split(args.psql or os.environ.get("HMS_PSQL") or DEFAULT_PSQL, posix=(os.name != "nt")) + PSQL_FLAGS


# untranslated, UTF-8 client messages so errors read the same everywhere
PSQL_ENV = {**os.environ, "LC_MESSAGES": "C", "PGCLIENTENCODING": "UTF8"}


def psql_query(args, sql: str) -> list[list[str | None]]:
    """Runs a query; returns rows as lists of strings, NULL as None."""
    cmd = psql_base(args) + ["-A", "-t", "-F", "\t", "-P", "null=\\N", "-c", SET_UTF8, "-c", sql]
    res = subprocess.run(cmd, capture_output=True, env=PSQL_ENV)
    if res.returncode != 0:
        raise ArchiveError(res.stderr.decode("utf-8", "replace").strip() or f"psql failed: {sql}")
    rows = []
    for line in res.stdout.decode("utf-8").splitlines():
        if line.strip():
            rows.append([None if v == "\\N" else v for v in line.split("\t")])
    return rows


def month(value: str) -> dt.date:
    m = re.fullmatch(r"(\d{4})-(\d{2})", value)
    if not m:
        raise argparse.ArgumentTypeError("month must look like 2026-10")
    return dt.date(int(m.group(1)), int(m.group(2)), 1)


def sql_text(value: str | None) -> str:
    return "NULL" if value is None else "'" + value.replace("'", "''") + "'"


def sql_num(value: int | None) -> str:
    return "NULL" if value is None else str(int(value))


def as_int(value: str | None) -> int | None:
    return None if value is None else int(value)


# --------------------------------------------------------------------------- blocks
def compress_block(raw: bytes) -> tuple[str, int, bytes]:
    # Dictionary sized to the block: a full preset dictionary (8 MiB) costs more
    # to initialise than compressing a small block (measured 2-3x slower).
    dict_size = 1 << max(16, (max(len(raw), 1) - 1).bit_length())
    filters = [{"id": lzma.FILTER_LZMA2, "preset": PRESET, "dict_size": dict_size}]
    return hashlib.sha256(raw).hexdigest(), len(raw), lzma.compress(
        raw, format=lzma.FORMAT_XZ, check=lzma.CHECK_CRC64, filters=filters)


def decompress_block(data: bytes) -> bytes:
    return lzma.decompress(data, format=lzma.FORMAT_XZ)


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(CHUNK), b""):
            h.update(chunk)
    return h.hexdigest()


def archive_name(period: dt.date) -> str:
    return f"audit_log_{period:%Y-%m}.zip"


def period_from_file(path: Path) -> dt.date:
    m = re.search(r"audit_log_(\d{4}-\d{2})\.zip$", path.name)
    if not m:
        raise ArchiveError(f"{path.name}: expected a file named audit_log_YYYY-MM.zip")
    return month(m.group(1))


def sidecar(path: Path) -> Path:
    return path.with_name(path.name + ".sha256")


def iter_segments(zf: zipfile.ZipFile, manifest: dict, pool: cf.Executor):
    """Yields, per segment, the decompressed column blocks (bytes, COPY text lines).
    The next segment is already being decompressed while the current one is used."""
    segments = manifest["segments"]
    if not segments:
        return

    def submit(seg):
        return [pool.submit(decompress_block, zf.read(b["member"])) for b in seg["blocks"]]

    pending = submit(segments[0])
    for i, seg in enumerate(segments):
        current = pending
        if i + 1 < len(segments):
            pending = submit(segments[i + 1])
        blocks = [f.result() for f in current]
        for block, meta in zip(blocks, seg["blocks"]):
            if hashlib.sha256(block).hexdigest() != meta["rawSha256"]:
                raise ArchiveError(f"block {meta['member']} is corrupt (SHA-256 mismatch)")
        yield seg, blocks


def segment_lines(blocks: list[bytes]) -> list[bytes]:
    """Turns column blocks back into COPY text rows (tab separated)."""
    columns = [b.split(b"\n")[:-1] for b in blocks]
    lengths = {len(c) for c in columns}
    if len(lengths) != 1:
        raise ArchiveError("columns of a segment have different row counts")
    return [b"\t".join(row) for row in zip(*columns)]


def csv_column(block: bytes) -> list[str]:
    """One column block (COPY text) -> CSV cells. Works on the whole column at once:
    columns without escapes or CSV special characters need no per-value work."""
    text = block.decode("utf-8")
    values = text.split("\n")[:-1]
    escaped = "\\" in text.replace("\\N", "")
    if escaped:
        values = ["" if v == "\\N" else ESCAPE_RE.sub(lambda m: ESCAPES[m.group(0)], v) for v in values]
    elif "\\N" in text:
        values = ["" if v == "\\N" else v for v in values]
    if escaped or "," in text or '"' in text:
        values = ['"' + v.replace('"', '""') + '"' if ("," in v or '"' in v or "\n" in v or "\r" in v) else v
                  for v in values]
    return values


def open_archive(path: Path) -> tuple[zipfile.ZipFile, dict]:
    if not path.is_file():
        raise ArchiveError(f"{path}: file not found")
    try:
        zf = zipfile.ZipFile(path)
        manifest = json.loads(zf.read("manifest.json"))
    except (zipfile.BadZipFile, KeyError, ValueError) as exc:
        raise ArchiveError(f"{path.name}: not a valid audit archive ({exc})")
    if manifest.get("format") != FORMAT:
        raise ArchiveError(f"{path.name}: unsupported format {manifest.get('format')}")
    return zf, manifest


def db_archive_record(args, period: dt.date) -> dict | None:
    rows = psql_query(args, "SELECT row_count, min_id, max_id, file_sha256, content_sha256, status "
                            f"FROM audit_archives WHERE period_start = DATE {sql_text(period.isoformat())}")
    if not rows:
        return None
    r = rows[0]
    return {"rows": as_int(r[0]), "minId": as_int(r[1]), "maxId": as_int(r[2]),
            "fileSha256": r[3], "contentSha256": r[4], "status": r[5]}


def verify_file(args, path: Path, check_db: bool, pool: cf.Executor) -> dict:
    """Full check: file hash, every block hash, content hash, row count and id range."""
    file_sha = sha256_file(path)
    problems = []
    side = sidecar(path)
    if side.is_file():
        expected = side.read_text(encoding="utf-8").split()[0].lower()
        if expected != file_sha:
            problems.append(f"{side.name} says {expected} but file has {file_sha}")
    try:
        zf, manifest = open_archive(path)
        content = hashlib.sha256()
        size = rows = 0
        min_id = max_id = None
        with zf:
            for seg, blocks in iter_segments(zf, manifest, pool):
                lines = segment_lines(blocks)
                data = b"\n".join(lines) + b"\n" if lines else b""
                content.update(data)
                size += len(data)
                rows += len(lines)
                if lines:
                    ids = blocks[0].rstrip(b"\n")
                    first, last = int(ids.split(b"\n", 1)[0]), int(ids.rsplit(b"\n", 1)[-1])
                    min_id = first if min_id is None else min(min_id, first)
                    max_id = last if max_id is None else max(max_id, last)
        actual = {"rows": rows, "minId": min_id, "maxId": max_id, "contentSha256": content.hexdigest()}
        for key, value in actual.items():
            if manifest.get(key) != value:
                problems.append(f"manifest {key}={manifest.get(key)} but archive content has {value}")
    except (ArchiveError, lzma.LZMAError, EOFError, zipfile.BadZipFile, ValueError, UnicodeDecodeError) as exc:
        problems.append(str(exc))
        actual = {}
    if check_db:
        rec = db_archive_record(args, period_from_file(path))
        if rec is None:
            problems.append("no audit_archives record for this month")
        else:
            for key, value in {"fileSha256": file_sha, **actual}.items():
                if rec.get(key) != value:
                    problems.append(f"database {key}={rec.get(key)} but archive has {value}")
    if problems:
        raise ArchiveError(f"{path.name} failed verification:\n  - " + "\n  - ".join(problems))
    return {"fileSha256": file_sha, **actual}


# --------------------------------------------------------------------------- commands
def cmd_list(args, pool) -> None:
    rows = psql_query(args, "SELECT period_start, partition_name, live, live_bytes, archive_status, "
                            "archived_rows, archive_file, archive_bytes FROM audit_partitions()")

    def kb(v):
        return "NULL" if v is None else f"{int(v) / 1024:.1f}"

    print(f"{'month':<8} {'partition':<20} {'live':<5} {'live KB':>9} {'archive':<18} {'rows':>9} {'file KB':>8}  file")
    for r in rows:
        print(f"{r[0][:7]:<8} {r[1]:<20} {r[2]:<5} {kb(r[3]):>9} {r[4] or 'NULL':<18} {r[5] or 'NULL':>9} "
              f"{kb(r[7]):>8}  {r[6] or 'NULL'}")


def cmd_export(args, pool) -> None:
    period = args.month
    out_dir = Path(args.out).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)
    partition = f"audit_log_y{period:%Y}m{period:%m}"
    target = out_dir / archive_name(period)
    if target.exists():
        raise ArchiveError(f"{target} already exists")
    partial = target.with_name(target.name + ".part")

    next_month = (period.replace(day=28) + dt.timedelta(days=4)).replace(day=1)
    # the closed-month check runs once, before any row is read (fails fast for an open month)
    copy_sql = (f"COPY (SELECT {', '.join(CSV_COLUMNS)} FROM audit_log_export "
                f"CROSS JOIN (SELECT audit_assert_closed_period(DATE {sql_text(period.isoformat())}) AS partition) closed "
                "WHERE closed.partition IS NOT NULL "
                f"AND occurred_at >= TIMESTAMPTZ {sql_text(period.isoformat() + ' 00:00+00')} "
                f"AND occurred_at < TIMESTAMPTZ {sql_text(next_month.isoformat() + ' 00:00+00')} "
                "ORDER BY id) TO STDOUT")
    proc = subprocess.Popen(psql_base(args) + ["-c", SET_UTF8, "-c", copy_sql],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=PSQL_ENV)
    content = hashlib.sha256()
    content_bytes = 0
    segments = []
    lines: list[bytes] = []
    rest = b""

    def flush(seg_lines: list[bytes]) -> None:
        nonlocal content_bytes
        data = b"\n".join(seg_lines) + b"\n"
        content.update(data)
        content_bytes += len(data)
        columns = list(zip(*(line.split(b"\t") for line in seg_lines)))
        if len(columns) != len(CSV_COLUMNS):
            raise ArchiveError(f"unexpected column count {len(columns)}")
        segments.append({
            "rows": len(seg_lines),
            "firstId": int(seg_lines[0].split(b"\t", 1)[0]),
            "lastId": int(seg_lines[-1].split(b"\t", 1)[0]),
            "futures": [pool.submit(compress_block, b"\n".join(c) + b"\n") for c in columns],
        })

    for chunk in iter(lambda: proc.stdout.read(CHUNK), b""):
        parts = (rest + chunk).split(b"\n")
        rest = parts.pop()
        lines.extend(p[:-1] if p.endswith(b"\r") else p for p in parts)
        while len(lines) >= SEGMENT_ROWS:
            flush(lines[:SEGMENT_ROWS])
            del lines[:SEGMENT_ROWS]
    err = proc.stderr.read().decode("utf-8", "replace").strip()
    if proc.wait() != 0:
        raise ArchiveError(err or "psql COPY failed")
    if rest.strip():
        raise ArchiveError("export stream ended in the middle of a row")
    if lines:
        flush(lines)

    # audit_archive_register() re-checks these against the partition before accepting the file
    rows = sum(s["rows"] for s in segments)
    actual = {"rows": rows,
              "minId": segments[0]["firstId"] if segments else None,
              "maxId": segments[-1]["lastId"] if segments else None}

    manifest_segments = []
    blobs = []
    for si, seg in enumerate(segments):
        blocks = []
        for ci, fut in enumerate(seg.pop("futures")):
            raw_sha, raw_len, blob = fut.result()
            member = f"{si:04d}/{ci:02d}.xz"
            blocks.append({"member": member, "column": CSV_COLUMNS[ci], "rawSha256": raw_sha,
                           "rawBytes": raw_len, "xzBytes": len(blob)})
            blobs.append((member, blob))
        manifest_segments.append({**seg, "blocks": blocks})
    # self-check: every block decompresses to exactly what was exported
    raw_shas = {b["member"]: b["rawSha256"] for s in manifest_segments for b in s["blocks"]}
    for (member, _), back in zip(blobs, pool.map(lambda mb: decompress_block(mb[1]), blobs)):
        if hashlib.sha256(back).hexdigest() != raw_shas[member]:
            raise ArchiveError(f"self-check failed for {member}")

    manifest = {
        "format": FORMAT, "dataset": "hms-community-audit-log", "period": f"{period:%Y-%m}",
        "partition": partition, "compression": COMPRESSION, "encoding": "PostgreSQL COPY text, one column per block",
        "columns": CSV_COLUMNS, **actual,
        "contentSha256": content.hexdigest(), "contentBytes": content_bytes,
        "createdAt": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "segments": manifest_segments,
    }
    with zipfile.ZipFile(partial, "w", compression=zipfile.ZIP_STORED) as zf:
        # xz blocks are stored as they are; only the manifest (repetitive JSON) is deflated
        zf.writestr("manifest.json", json.dumps(manifest, separators=(",", ":")),
                    compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
        for member, blob in blobs:
            zf.writestr(member, blob)
    file_sha = sha256_file(partial)
    file_bytes = partial.stat().st_size
    try:
        psql_query(args, "SELECT audit_archive_register("
                         f"DATE {sql_text(period.isoformat())}, {sql_text(target.name)}, {rows}, "
                         f"{sql_num(actual['minId'])}, {sql_num(actual['maxId'])}, {sql_text(file_sha)}, "
                         f"{sql_text(content.hexdigest())}, {file_bytes}, {content_bytes}, {sql_text(COMPRESSION)})")
    except ArchiveError:
        partial.unlink(missing_ok=True)
        raise
    partial.replace(target)
    sidecar(target).write_text(f"{file_sha}  {target.name}\n", encoding="utf-8")
    ratio = (content_bytes / file_bytes) if file_bytes and content_bytes else 0
    print(f"exported {rows} rows -> {target}")
    print(f"  content {content_bytes:,} bytes -> archive {file_bytes:,} bytes (x{ratio:.1f}), "
          f"{sum(len(s['blocks']) for s in manifest_segments)} blocks")
    print(f"  sha256 {file_sha}")


def cmd_verify(args, pool) -> None:
    info = verify_file(args, Path(args.file), args.check_db, pool)
    ids = "NULL (no rows)" if info["minId"] is None else f"{info['minId']}..{info['maxId']}"
    print(f"OK {Path(args.file).name}: {info['rows']} rows, ids {ids}, sha256 {info['fileSha256']}")


def cmd_open(args, pool) -> None:
    src = Path(args.file)
    dest = Path(args.output) if args.output else src.with_suffix(".csv")
    zf, manifest = open_archive(src)
    rows = 0
    with zf, open(dest, "w", encoding="utf-8-sig", newline="") as out:
        out.write(",".join(CSV_COLUMNS) + "\r\n")
        for seg, blocks in iter_segments(zf, manifest, pool):
            columns = [csv_column(b) for b in blocks]
            out.write("\r\n".join(map(",".join, zip(*columns))))
            out.write("\r\n")
            rows += seg["rows"]
    if rows != manifest["rows"]:
        raise ArchiveError(f"wrote {rows} rows but manifest has {manifest['rows']}")
    print(f"opened {rows} rows -> {dest}")


def cmd_drop(args, pool) -> None:
    period = args.month
    archive = Path(args.archive_dir) / archive_name(period)
    verify_file(args, archive, True, pool)
    if not args.yes:
        raise ArchiveError("refusing to drop without --yes (the archive file verified OK)")
    rows = psql_query(args, f"SELECT audit_archive_drop_partition(DATE {sql_text(period.isoformat())})")
    print(f"dropped live partition for {period:%Y-%m} ({rows[0][0]} rows kept in {archive.name})")


def cmd_restore(args, pool) -> None:
    src = Path(args.file)
    period = period_from_file(src)
    rec = db_archive_record(args, period)
    file_sha = sha256_file(src)
    if rec is None:
        raise ArchiveError(f"no audit_archives record for {period:%Y-%m}")
    if rec["fileSha256"] != file_sha:
        raise ArchiveError(f"{src.name} is not the registered archive (sha256 {file_sha}, expected {rec['fileSha256']})")
    zf, manifest = open_archive(src)
    day = sql_text(period.isoformat())
    copy_cols = ("id, occurred_at, actor_user_id, actor_username, actor_role, action, action_category, "
                 "result, entity_type, entity_id, session_id, details")
    # one transaction: begin (state check) -> COPY -> finish (count/id check); any failure rolls back
    cmd = psql_base(args) + ["-A", "-t", "--single-transaction",
                             "-c", SET_UTF8,
                             "-c", f"SELECT audit_restore_begin(DATE {day})",
                             "-c", f"COPY audit_log_restored ({copy_cols}) FROM STDIN",
                             "-c", f"SELECT audit_restore_finish(DATE {day})"]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=PSQL_ENV)
    content = hashlib.sha256()
    try:
        with zf:
            for seg, blocks in iter_segments(zf, manifest, pool):
                lines = segment_lines(blocks)
                data = b"\n".join(lines) + b"\n"
                content.update(data)
                proc.stdin.write(data)
        proc.stdin.close()
    except OSError:
        pass  # psql stopped reading (it reports why on stderr below); the transaction is rolled back
    except ArchiveError as exc:
        proc.kill()
        proc.wait()
        raise ArchiveError(f"restore aborted, nothing was loaded: {exc}")
    out, err = proc.communicate()
    err = err.decode("utf-8", "replace").strip()
    if proc.returncode != 0:
        raise ArchiveError(err or "restore failed")
    out = out.decode("utf-8", "replace")
    if content.hexdigest() != rec["contentSha256"]:
        raise ArchiveError("restored content differs from the registered archive")
    restored = [line for line in out.splitlines() if line.strip()]
    print(f"restored {restored[-1] if restored else '?'} rows for {period:%Y-%m} (searchable with audit_search())")


def cmd_unload(args, pool) -> None:
    rows = psql_query(args, f"SELECT audit_restore_unload(DATE {sql_text(args.month.isoformat())})")
    print(f"unloaded {rows[0][0]} restored rows for {args.month:%Y-%m}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--psql", help=f"psql command (default: HMS_PSQL or '{DEFAULT_PSQL}')")
    parser.add_argument("--workers", type=int, default=os.cpu_count() or 4, help="parallel threads (default: CPU count)")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("list").set_defaults(func=cmd_list)
    p = sub.add_parser("export")
    p.add_argument("--month", type=month, required=True)
    p.add_argument("--out", default="audit-archives")
    p.set_defaults(func=cmd_export)
    p = sub.add_parser("verify")
    p.add_argument("file")
    p.add_argument("--check-db", action="store_true")
    p.set_defaults(func=cmd_verify)
    p = sub.add_parser("open")
    p.add_argument("file")
    p.add_argument("--output")
    p.set_defaults(func=cmd_open)
    p = sub.add_parser("drop")
    p.add_argument("--month", type=month, required=True)
    p.add_argument("--archive-dir", default="audit-archives")
    p.add_argument("--yes", action="store_true")
    p.set_defaults(func=cmd_drop)
    p = sub.add_parser("restore")
    p.add_argument("file")
    p.set_defaults(func=cmd_restore)
    p = sub.add_parser("unload")
    p.add_argument("--month", type=month, required=True)
    p.set_defaults(func=cmd_unload)

    args = parser.parse_args(argv)
    started = time.perf_counter()
    try:
        with cf.ThreadPoolExecutor(max_workers=max(1, args.workers)) as pool:
            args.func(args, pool)
    except ArchiveError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(f"  took {time.perf_counter() - started:.2f} s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
