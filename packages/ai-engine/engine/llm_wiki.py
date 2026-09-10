"""
LLM Wiki 知识编译器（MVP）。

该模块只依赖 Python 标准库，负责把原始 Markdown/文本素材编译成受控目录中的
结构化 Markdown，并维护一个轻量 JSON 索引供后续 Harness/RAG 检索使用。
"""

from __future__ import annotations

import json
import os
import re
import tempfile
import threading
from copy import deepcopy
from pathlib import Path
from typing import Any, Iterable


class WikiCompileError(ValueError):
    """Wiki 素材校验或编译失败。"""


class WikiCompiler:
    """Wiki 知识编译器：素材 -> 结构化互联 Markdown 百科。"""

    INDEX_FILE_NAME = "index.json"
    MIN_CONTENT_LENGTH = 10
    LINK_PATTERN = re.compile(r"\[\[([^\[\]]+)\]\]")
    HEADING_PATTERN = re.compile(r"^\s*#\s+(.+?)\s*$", re.MULTILINE)
    UNSAFE_NAME_PATTERN = re.compile(r'[\x00-\x1f<>:"/\\|?*]+')

    def __init__(self, wiki_dir: str | Path, build_dir: str | Path) -> None:
        """初始化原始素材目录和编译产物目录。"""
        self.wiki_dir = Path(wiki_dir).expanduser()
        self.build_dir = Path(build_dir).expanduser().resolve()
        self.build_dir.mkdir(parents=True, exist_ok=True)
        self._index_lock = threading.RLock()
        self._cached_index: dict[str, Any] | None = None
        self._cached_index_signature: tuple[int, int, int] | None = None

    def compile_source(
        self,
        raw_text: str,
        title: str | None = None,
        scenic_area_id: int | None = None,
        tags: Iterable[str] | None = None,
    ) -> str:
        """
        编译一份原始素材并返回相对构建目录的 Markdown 路径。

        ``scenic_area_id`` 是可选扩展参数；传入后产物固定写入
        ``wiki/scenic_<id>/``，与业务后端 Wiki 条目契约一致。
        """
        body = self._clean_source(raw_text)
        resolved_title = self._resolve_title(body, title)
        relative_path = self._build_relative_path(resolved_title, scenic_area_id)
        target = self._resolve_under_build(relative_path)
        markdown = self._format_markdown(body, resolved_title)
        self._atomic_write_text(target, markdown)

        links = self._extract_links(markdown)
        self._upsert_index_entry(
            {
                "title": resolved_title,
                "file_path": relative_path.as_posix(),
                "tags": self._normalize_values(tags),
                "links": links,
                "content": markdown,
            }
        )
        self.build_links(relative_path.as_posix())
        return relative_path.as_posix()

    def build_links(self, page_path: str) -> list[str]:
        """扫描页面与现有条目标题，补充 ``[[wiki-links]]`` 并返回实际链接。"""
        target = self._resolve_under_build(page_path)
        if not target.is_file():
            raise WikiCompileError("Wiki 页面不存在")

        content = target.read_text(encoding="utf-8")
        current_title = self._title_from_markdown(content) or target.stem
        links = self._extract_links(content)
        with self._index_lock:
            index = self._read_index()
            known_titles = {
                str(entry.get("title")).strip()
                for entry in index.get("entries", [])
                if isinstance(entry, dict) and entry.get("title") and entry.get("file_path")
            }

            for known_title in known_titles:
                if known_title != current_title and known_title in content:
                    canonical = self._canonical_link(known_title)
                    if canonical not in links and known_title in content:
                        links.append(canonical)

            if links:
                content = self._append_links_section(content, links)
                self._atomic_write_text(target, content)

            entry = self._find_index_entry(index, target.relative_to(self.build_dir).as_posix())
            if entry is not None:
                entry["links"] = links
                entry["content"] = content
                self._atomic_write_json(self._index_path(), index)
        return links

    def retrieve(
        self,
        query: str,
        scenic_area_id: int | None = None,
        index: dict[str, Any] | None = None,
    ) -> list[dict[str, Any]]:
        """按标题、标签和正文关键词检索 Wiki 索引，返回相关度降序结果。"""
        normalized_query = self._clean_query(query)
        if not normalized_query:
            return []
        query_terms = self._terms(normalized_query)
        results: list[dict[str, Any]] = []
        index_snapshot = index if index is not None else self._read_index()
        for entry in index_snapshot.get("entries", []):
            if not isinstance(entry, dict):
                continue
            if scenic_area_id is not None and not self._entry_matches_scenic_area(
                entry, scenic_area_id
            ):
                continue
            title = str(entry.get("title") or "")
            tags = " ".join(self._normalize_values(entry.get("tags")))
            content = str(entry.get("content") or "")
            haystack = f"{title} {tags} {content}".lower()
            score = sum(haystack.count(term.lower()) for term in query_terms)
            if title.lower() == normalized_query.lower():
                score += 100
            elif normalized_query.lower() in title.lower():
                score += 20
            if score <= 0:
                continue
            results.append(
                {
                    "title": title,
                    "file_path": str(entry.get("file_path") or ""),
                    "tags": self._normalize_values(entry.get("tags")),
                    "links": self._normalize_values(entry.get("links")),
                    "content": content,
                    "score": score,
                }
            )
        results.sort(key=lambda item: (-int(item["score"]), str(item["file_path"])))
        return results

    def build_all(self) -> list[str]:
        """批量编译 ``wiki_dir`` 下的 Markdown/文本素材并建立链接。"""
        if not self.wiki_dir.exists():
            return []
        outputs: list[str] = []
        for source in sorted(self.wiki_dir.rglob("*")):
            if not source.is_file() or source.suffix.lower() not in {".md", ".markdown", ".txt"}:
                continue
            raw_text = source.read_text(encoding="utf-8-sig")
            output = self.compile_source(raw_text, title=source.stem)
            outputs.append(output)
        for output in outputs:
            self.build_links(output)
        return outputs

    def _clean_source(self, raw_text: str) -> str:
        if not isinstance(raw_text, str):
            raise WikiCompileError("素材内容必须是文本")
        cleaned = raw_text.replace("\r\n", "\n").replace("\r", "\n").replace("\x00", "")
        cleaned = "\n".join(line.rstrip() for line in cleaned.split("\n")).strip()
        if len(re.sub(r"\s+", "", cleaned)) < self.MIN_CONTENT_LENGTH:
            raise WikiCompileError("素材内容不足，无法编译 Wiki 条目")
        return cleaned

    def _resolve_title(self, body: str, title: str | None) -> str:
        candidate = (title or self._title_from_markdown(body) or "未命名条目").strip()
        candidate = re.sub(r"\s+", " ", candidate)
        if not candidate or len(candidate) > 100:
            raise WikiCompileError("Wiki 标题格式错误")
        return candidate

    def _title_from_markdown(self, content: str) -> str | None:
        match = self.HEADING_PATTERN.search(content)
        return match.group(1).strip() if match else None

    def _format_markdown(self, body: str, title: str) -> str:
        without_heading = re.sub(r"^\s*#\s+.+?\s*(?:\n|$)", "", body, count=1).strip()
        return f"# {title}\n\n{without_heading}\n"

    def _build_relative_path(self, title: str, scenic_area_id: int | None) -> Path:
        safe_title = self.UNSAFE_NAME_PATTERN.sub("_", title).strip(" .")
        safe_title = re.sub(r"\.{2,}", "_", safe_title)[:80] or "wiki-entry"
        if scenic_area_id is None:
            return Path("wiki") / f"{safe_title}.md"
        if not isinstance(scenic_area_id, int) or scenic_area_id <= 0:
            raise WikiCompileError("景区 ID 必须为正整数")
        return Path("wiki") / f"scenic_{scenic_area_id}" / f"{safe_title}.md"

    def _resolve_under_build(self, relative_or_absolute: str | Path) -> Path:
        candidate = Path(relative_or_absolute)
        if candidate.is_absolute():
            target = candidate.resolve()
        else:
            target = (self.build_dir / candidate).resolve()
        try:
            target.relative_to(self.build_dir)
        except ValueError as exc:
            raise WikiCompileError("Wiki 路径越界") from exc
        if target == self.build_dir or (
            target.name == self.INDEX_FILE_NAME and target.parent != self.build_dir
        ):
            raise WikiCompileError("Wiki 路径格式错误")
        return target

    def _extract_links(self, content: str) -> list[str]:
        links: list[str] = []
        for raw_link in self.LINK_PATTERN.findall(content):
            title = re.sub(r"\s+", " ", raw_link).strip()
            if title:
                canonical = self._canonical_link(title)
                if canonical not in links:
                    links.append(canonical)
        return links

    def _canonical_link(self, title: str) -> str:
        return f"wiki/{title.strip().removesuffix('.md')}"

    def _append_links_section(self, content: str, links: list[str]) -> str:
        bullet_list = "\n".join(f"- [[{link.removeprefix('wiki/')}]]" for link in links)
        section = "\n\n## 相关 Wiki 条目\n\n" + bullet_list
        if "## 相关 Wiki 条目" in content:
            return content
        return content.rstrip() + section + "\n"

    def _index_path(self) -> Path:
        return self.build_dir / self.INDEX_FILE_NAME

    def _read_index(self) -> dict[str, Any]:
        """读取索引快照；文件未变化时复用内存缓存。"""
        path = self._index_path()
        with self._index_lock:
            signature = self._index_signature(path)
            if self._cached_index is not None and signature == self._cached_index_signature:
                return deepcopy(self._cached_index)
            if signature is None:
                payload: dict[str, Any] = {"version": 1, "entries": []}
            else:
                try:
                    payload = json.loads(path.read_text(encoding="utf-8"))
                except (OSError, json.JSONDecodeError) as exc:
                    raise WikiCompileError("Wiki 索引损坏，无法读取") from exc
                if not isinstance(payload, dict) or not isinstance(payload.get("entries"), list):
                    raise WikiCompileError("Wiki 索引格式错误")
            self._cached_index = deepcopy(payload)
            self._cached_index_signature = signature
            return deepcopy(payload)

    def _upsert_index_entry(self, entry: dict[str, Any]) -> None:
        with self._index_lock:
            index = self._read_index()
            entries = index.setdefault("entries", [])
            existing = self._find_index_entry(index, str(entry["file_path"]))
            if existing is None:
                entries.append(entry)
            else:
                existing.update(entry)
            entries.sort(key=lambda item: str(item.get("file_path", "")))
            self._atomic_write_json(self._index_path(), index)

    def _find_index_entry(self, index: dict[str, Any], file_path: str) -> dict[str, Any] | None:
        for entry in index.get("entries", []):
            if isinstance(entry, dict) and entry.get("file_path") == file_path:
                return entry
        return None

    def _atomic_write_text(self, target: Path, content: str) -> None:
        target.parent.mkdir(parents=True, exist_ok=True)
        self._atomic_write(target, content.encode("utf-8"))

    def _atomic_write_json(self, target: Path, payload: dict[str, Any]) -> None:
        serialized = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
        if target == self._index_path():
            with self._index_lock:
                self._atomic_write(target, serialized.encode("utf-8"))
                self._cached_index = deepcopy(payload)
                self._cached_index_signature = self._index_signature(target)
            return
        self._atomic_write(target, serialized.encode("utf-8"))

    def _atomic_write(self, target: Path, data: bytes) -> None:
        target.parent.mkdir(parents=True, exist_ok=True)
        descriptor, temporary_name = tempfile.mkstemp(prefix=f".{target.name}.", dir=target.parent)
        try:
            with os.fdopen(descriptor, "wb") as temporary:
                temporary.write(data)
                temporary.flush()
                os.fsync(temporary.fileno())
            os.replace(temporary_name, target)
        except OSError:
            try:
                os.unlink(temporary_name)
            except OSError:
                pass
            raise

    @staticmethod
    def _index_signature(path: Path) -> tuple[int, int, int] | None:
        try:
            stat = path.stat()
            return stat.st_mtime_ns, stat.st_ctime_ns, stat.st_size
        except FileNotFoundError:
            return None
        except OSError as exc:
            raise WikiCompileError("Wiki 索引状态无法读取") from exc

    @staticmethod
    def _normalize_values(values: Any) -> list[str]:
        if isinstance(values, str):
            values = [values]
        if not isinstance(values, Iterable) or isinstance(values, (bytes, dict)):
            return []
        normalized: list[str] = []
        for value in values:
            if isinstance(value, str) and value.strip() and value.strip() not in normalized:
                normalized.append(value.strip())
        return normalized

    @staticmethod
    def _clean_query(query: str) -> str:
        return query.strip() if isinstance(query, str) else ""

    @staticmethod
    def _terms(query: str) -> list[str]:
        segments = [segment for segment in re.split(r"\s+|[,，。！？；：、？]+", query) if segment]
        terms: list[str] = []
        for segment in segments:
            if segment not in terms:
                terms.append(segment)
            if re.search(r"[\u3400-\u9fff]", segment):
                for index in range(len(segment) - 1):
                    gram = segment[index : index + 2]
                    if gram not in terms:
                        terms.append(gram)
        return terms or [query]

    @staticmethod
    def _entry_matches_scenic_area(entry: dict[str, Any], scenic_area_id: int) -> bool:
        return f"scenic_{scenic_area_id}/" in str(entry.get("file_path", ""))
