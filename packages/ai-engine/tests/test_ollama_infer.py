"""Ollama 客户端适配测试。"""

from __future__ import annotations

import unittest

from engine.ollama_infer import (
    OllamaClient,
    OllamaModelError,
    OllamaResponseError,
    OllamaUnavailableError,
)


class FakeMessage:
    def __init__(self, content: str) -> None:
        self.content = content


class FakeOllama:
    def __init__(self, response: object = None, vector: object = None) -> None:
        self.response = response
        self.vector = vector
        self.chat_calls: list[dict[str, object]] = []

    def chat(self, **kwargs: object) -> object:
        self.chat_calls.append(kwargs)
        return self.response

    def embeddings(self, **_kwargs: object) -> object:
        return self.vector


class OllamaClientTest(unittest.TestCase):
    """验证 SDK 响应规范化与依赖异常。"""

    def test_chat_normalizes_mapping_and_object_responses(self) -> None:
        for response in (
            {"message": {"content": "  遵义会议是重要转折点。  "}},
            FakeMessage("  遵义会议是重要转折点。  "),
        ):
            client = OllamaClient(client=FakeOllama(response=response))
            self.assertEqual(
                "遵义会议是重要转折点。",
                client.chat([{"role": "user", "content": "问题"}]),
            )

    def test_chat_stream_returns_only_text_chunks(self) -> None:
        client = OllamaClient(client=FakeOllama(response=[
            {"message": {"content": "第一句"}},
            {"message": {"content": "第二句"}},
        ]))

        result = client.chat([{"role": "user", "content": "问题"}], stream=True)

        self.assertEqual(["第一句", "第二句"], list(result))

    def test_embedding_normalizes_numeric_vector(self) -> None:
        client = OllamaClient(client=FakeOllama(vector={"embedding": [1, 2.5, 3]}))

        self.assertEqual([1.0, 2.5, 3.0], client.embedding("遵义会议"))

    def test_rejects_invalid_messages_and_empty_embedding(self) -> None:
        client = OllamaClient(client=FakeOllama(vector={"embedding": []}))

        with self.assertRaises(OllamaResponseError):
            client.chat([])
        with self.assertRaises(OllamaResponseError):
            client.embedding(" ")
        with self.assertRaises(OllamaResponseError):
            client.embedding("问题")

    def test_classifies_unavailable_and_missing_model_errors(self) -> None:
        unavailable = OllamaClient(client=FakeOllama())
        unavailable._client.chat = lambda **_kwargs: (_ for _ in ()).throw(
            ConnectionError("connection refused")
        )
        with self.assertRaises(OllamaUnavailableError):
            unavailable.chat([{"role": "user", "content": "问题"}])

        missing = OllamaClient(client=FakeOllama())
        missing._client.chat = lambda **_kwargs: (_ for _ in ()).throw(
            RuntimeError("model not found")
        )
        with self.assertRaises(OllamaModelError):
            missing.chat([{"role": "user", "content": "问题"}])


if __name__ == "__main__":
    unittest.main()
