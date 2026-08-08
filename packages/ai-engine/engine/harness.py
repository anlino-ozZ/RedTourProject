"""
机架工程 Harness 模块
=====================
六组件：执行环境、工具注册表、上下文管理器、状态存储、生命周期钩子、评估接口。

策略：
- LocalContextMiddleware：注入环境地图（景点分布/历史条目索引/CV 指令），
  实现零回合定向（无需多轮对话即可定位游客所在景点）
- 推理三明治：关键决策阶段用高推理模型，润色阶段用轻量模型
- 验证门 + todo.md 协议：输出前核对 Wiki 权威引用，冲突则回滚
"""

from typing import Any


class LocalContextMiddleware:
    """本地上下文中间件：注入环境地图实现零回合定向"""

    def inject_context(self, location: str | None = None) -> dict[str, Any]:
        """
        根据游客位置注入上下文：
        - 景点分布地图
        - 历史条目索引
        - CV（计算机视觉）指令

        TODO: 实现上下文注入逻辑
        """
        pass  # TODO


class Harness:
    """AI 推理机架：编排 LLM Wiki 检索、推理三明治、验证门"""

    def __init__(self) -> None:
        self.context_middleware = LocalContextMiddleware()

    def reasoning_sandwich(self, query: str, context: dict[str, Any]) -> str:
        """
        推理三明治策略：
        - 规划阶段：高推理模型分析意图、拆解子问题
        - 执行阶段：检索 Wiki、调用工具
        - 润色阶段：轻量模型生成最终自然语言回答

        TODO: 实现三阶段推理流程
        """
        pass  # TODO

    def verify_gate(self, answer: str, references: list[dict[str, Any]]) -> bool:
        """
        验证门：输出前核对 Wiki 权威引用。
        - 检查回答中的事实声明是否有 Wiki 引用支撑
        - 引用冲突时回滚，返回 False

        TODO: 实现验证逻辑
        """
        pass  # TODO

    def update_todo(self, tasks: list[str]) -> None:
        """
        维护 todo.md 协议：记录当前任务进度与未完成项。
        - 供跨会话状态恢复与多步骤追踪

        TODO: 实现 todo.md 读写
        """
        pass  # TODO

    def answer(self, question: str, location: str | None = None) -> str:
        """
        完整问答流程：
        1. LocalContextMiddleware 注入上下文（零回合定向）
        2. reasoning_sandwich 推理三明治生成回答
        3. verify_gate 验证门核对引用
        4. 验证通过返回回答，冲突则回滚重试

        TODO: 实现完整编排逻辑
        """
        pass  # TODO
