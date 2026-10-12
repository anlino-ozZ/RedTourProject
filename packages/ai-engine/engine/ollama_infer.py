"""
Ollama 本地大模型推理模块
=========================
封装 ollama Python SDK，提供对话与向量化能力。
模型运行在本地（如 qwen2.5:7b），全程离线，适配边缘部署场景。
"""

from typing import Any


class OllamaClient:
    """Ollama 客户端：对话推理与文本向量化"""

    def __init__(self, host: str = "http://localhost:11434", model: str = "qwen2.5:7b") -> None:
        """
        :param host: Ollama 服务地址
        :param model: 默认推理模型名
        """
        self.host = host
        self.model = model
        self._client: Any = None  # ollama.Client 实例，延迟初始化

    def _get_client(self) -> Any:
        """延迟初始化 Ollama 客户端"""
        if self._client is None:
            try:
                import ollama

                self._client = ollama.Client(host=self.host)
            except ImportError:
                raise RuntimeError("ollama 库未安装，请先 pip install ollama")
        return self._client

    def chat(self, messages: list[dict[str, str]], model: str | None = None, stream: bool = False) -> Any:
        """
        对话推理。
        :param messages: 消息列表 [{"role": "user", "content": "..."}]
        :param model: 指定模型，默认用初始化时的模型
        :param stream: 是否流式返回
        :return: 模型回复（流式时为生成器）

        TODO: 封装 ollama.Client.chat 调用，处理流式与非流式两种模式
        """
        pass  # TODO

    def embedding(self, text: str, model: str | None = None) -> list[float]:
        """
        文本向量化（用于 Wiki 检索相似度计算）。
        :param text: 待向量化的文本
        :param model: 指定 embedding 模型
        :return: 向量列表

        TODO: 封装 ollama.Client.embeddings 调用
        """
        pass  # TODO
